#!/usr/bin/env python3
"""Tiny lobby list for Cue Duel. Hosts register, the app lists them.
Passwords are checked by the host phone, the server never sees them."""
import asyncio
import base64
import hashlib
import json
import os
import re
import secrets
import struct
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TTL = 45
MAX_LOBBIES = 300
MAX_PER_IP = 3
TRUST_PROXY = os.environ.get("TRUST_PROXY") == "1"

lock = threading.Lock()
lobbies = {}


def clean_name(s):
    s = re.sub(r"[\x00-\x1f\x7f]", "", str(s)).strip()
    return s[:24] or "Cue Duel"


def purge():
    now = time.time()
    for k in [k for k, v in lobbies.items() if now - v["seen"] > TTL]:
        del lobbies[k]


class H(BaseHTTPRequestHandler):
    server_version = "CueDuelLobby"

    def log_message(self, *a):
        pass

    def ip(self):
        if TRUST_PROXY:
            f = self.headers.get("X-Forwarded-For", "")
            if f:
                return f.split(",")[0].strip()
        return self.client_address[0]

    def send(self, code, obj):
        b = json.dumps(obj).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def body(self):
        try:
            n = min(int(self.headers.get("Content-Length", "0")), 2048)
            d = json.loads(self.rfile.read(n) or b"{}")
            return d if isinstance(d, dict) else {}
        except Exception:
            return {}

    def do_GET(self):
        if self.path.split("?")[0] != "/v1/lobbies":
            return self.send(404, {})
        me = self.ip()
        with lock:
            purge()
            out = []
            for k, v in sorted(lobbies.items(), key=lambda x: -x[1]["created"]):
                same = v["ip"] == me
                if not v["listed"]:
                    continue
                out.append({"id": k, "name": v["name"], "ip": v["ip"], "lans": v["lans"], "same_net": same,
                            "port": v["port"], "open": v["open"], "locked": v["locked"], "players": v["players"],
                            "version": v["version"]})
        self.send(200, out)

    def do_POST(self):
        p = self.path.split("?")[0].rstrip("/")
        d = self.body()
        if p == "/v1/lobbies":
            ip = self.ip()
            try:
                port = int(d.get("port", 24680))
            except Exception:
                port = 0
            if not 1024 <= port <= 65535:
                return self.send(400, {})
            raw = d.get("lans", [])
            if not isinstance(raw, list):
                raw = []
            if not raw and isinstance(d.get("lan"), str):
                raw = [d["lan"]]  # ältere App-Versionen
            lans = [x for x in raw[:6] if isinstance(x, str) and re.fullmatch(
                r"(192\.168|10\.\d{1,3}|172\.(1[6-9]|2\d|3[01]))\.\d{1,3}\.\d{1,3}", x)]
            with lock:
                purge()
                if len(lobbies) >= MAX_LOBBIES or sum(1 for v in lobbies.values() if v["ip"] == ip) >= MAX_PER_IP:
                    return self.send(429, {})
                lid = secrets.token_hex(4)
                tok = secrets.token_hex(16)
                lobbies[lid] = {"name": clean_name(d.get("name", "")), "ip": ip, "port": port,
                                "locked": bool(d.get("locked", False)), "players": 1,
                                "open": bool(d.get("open", False)), "lans": lans, "listed": bool(d.get("listed", True)),
                                "version": str(d.get("version", ""))[:16], "token": tok,
                                "created": time.time(), "seen": time.time()}
            return self.send(200, {"id": lid, "token": tok})
        m = re.fullmatch(r"/v1/lobbies/([0-9a-f]{8})/(ping|close)", p)
        if not m:
            return self.send(404, {})
        with lock:
            v = lobbies.get(m.group(1))
            if v is None or not secrets.compare_digest(v["token"], str(d.get("token", ""))):
                return self.send(404, {})
            if m.group(2) == "close":
                del lobbies[m.group(1)]
            else:
                v["seen"] = time.time()
                v["players"] = 2 if d.get("players") == 2 else 1
        self.send(200, {})


# ---------- relay (WebSocket) ----------
# Wenn die Handys sich nicht direkt erreichen, schickt der Server die Nachrichten weiter.

GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
rooms = {}


async def ws_handshake(reader, writer):
    data = await asyncio.wait_for(reader.readuntil(b"\r\n\r\n"), 10)
    hdr = {}
    for line in data.decode("latin1").split("\r\n")[1:]:
        if ":" in line:
            k, v = line.split(":", 1)
            hdr[k.strip().lower()] = v.strip()
    key = hdr.get("sec-websocket-key")
    if not key or "websocket" not in hdr.get("upgrade", "").lower():
        return False
    acc = base64.b64encode(hashlib.sha1((key + GUID).encode()).digest()).decode()
    writer.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                  "Sec-WebSocket-Accept: %s\r\n\r\n" % acc).encode())
    await writer.drain()
    return True


async def ws_read(reader):
    b = await reader.readexactly(2)
    op = b[0] & 0x0F
    if not b[0] & 0x80:
        raise ValueError("fragmented")
    ln = b[1] & 0x7F
    if ln == 126:
        ln = struct.unpack(">H", await reader.readexactly(2))[0]
    elif ln == 127:
        ln = struct.unpack(">Q", await reader.readexactly(8))[0]
    if ln > 65536:
        raise ValueError("too big")
    mask = await reader.readexactly(4) if b[1] & 0x80 else b""
    data = await reader.readexactly(ln)
    if mask:
        data = bytes(c ^ mask[i % 4] for i, c in enumerate(data))
    return op, data


def ws_frame(op, payload):
    n = len(payload)
    if n < 126:
        head = bytes([0x80 | op, n])
    elif n < 65536:
        head = bytes([0x80 | op, 126]) + struct.pack(">H", n)
    else:
        head = bytes([0x80 | op, 127]) + struct.pack(">Q", n)
    return head + payload


class Conn:
    def __init__(self, writer):
        self.writer = writer

    async def send(self, obj):
        await self.send_raw(json.dumps(obj).encode())

    async def send_raw(self, data, op=1):
        try:
            self.writer.write(ws_frame(op, data))
            await self.writer.drain()
        except Exception:
            pass


async def relay_client(reader, writer):
    c = Conn(writer)
    room = None
    role = ""
    rid = ""
    try:
        if not await ws_handshake(reader, writer):
            return
        op, data = await asyncio.wait_for(ws_read(reader), 10)
        msg = json.loads(data)
        role = str(msg.get("t", ""))
        rid = str(msg.get("id", ""))
        with lock:
            v = lobbies.get(rid)
            token_ok = v is not None and secrets.compare_digest(v["token"], str(msg.get("token", "")))
        if role == "host" and token_ok:
            room = rooms.setdefault(rid, {})
            old = room.get("host")
            if old is not None:
                old.writer.close()
            room["host"] = c
            await c.send({"t": "ok"})
        elif role == "guest" and v is not None and rid in rooms and rooms[rid].get("host") and not rooms[rid].get("guest"):
            room = rooms[rid]
            room["guest"] = c
            with lock:
                if rid in lobbies:
                    lobbies[rid]["players"] = 2
            await room["host"].send({"t": "peer"})
            await c.send({"t": "ok"})
        else:
            await c.send({"t": "error"})
            return
        other_key = "guest" if role == "host" else "host"
        while True:
            op, data = await ws_read(reader)
            if op == 8:
                break
            if op == 9:
                await c.send_raw(data, 10)
            elif op == 1:
                o = room.get(other_key)
                if o is not None:
                    await o.send_raw(data)
                with lock:
                    if rid in lobbies:
                        lobbies[rid]["seen"] = time.time()
    except Exception:
        pass
    finally:
        if room is not None and room.get(role) is c:
            del room[role]
            o = room.get("guest" if role == "host" else "host")
            if o is not None:
                await o.send({"t": "bye"})
            if role == "guest":
                with lock:
                    if rid in lobbies:
                        lobbies[rid]["players"] = 1
            if not room:
                rooms.pop(rid, None)
        try:
            writer.close()
        except Exception:
            pass


async def run_relay(host, port):
    srv = await asyncio.start_server(relay_client, host, port)
    async with srv:
        await srv.serve_forever()


if __name__ == "__main__":
    host = os.environ.get("HOST", "127.0.0.1")
    port = int(os.environ.get("PORT", "8787"))
    ws_port = int(os.environ.get("WS_PORT", str(port + 1)))
    httpd = ThreadingHTTPServer((host, port), H)
    threading.Thread(target=httpd.serve_forever, daemon=True).start()
    asyncio.run(run_relay(host, ws_port))
