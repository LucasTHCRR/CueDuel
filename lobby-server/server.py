#!/usr/bin/env python3
"""Tiny lobby list for Cue Duel. Hosts register, the app lists them.
Passwords are checked by the host phone, the server never sees them."""
import json
import os
import re
import secrets
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
        with lock:
            purge()
            out = [{"id": k, "name": v["name"], "ip": v["ip"], "port": v["port"],
                    "locked": v["locked"], "players": v["players"], "version": v["version"]}
                   for k, v in sorted(lobbies.items(), key=lambda x: -x[1]["created"])]
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
            with lock:
                purge()
                if len(lobbies) >= MAX_LOBBIES or sum(1 for v in lobbies.values() if v["ip"] == ip) >= MAX_PER_IP:
                    return self.send(429, {})
                lid = secrets.token_hex(4)
                tok = secrets.token_hex(16)
                lobbies[lid] = {"name": clean_name(d.get("name", "")), "ip": ip, "port": port,
                                "locked": bool(d.get("locked", False)), "players": 1,
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


if __name__ == "__main__":
    host = os.environ.get("HOST", "127.0.0.1")
    port = int(os.environ.get("PORT", "8787"))
    ThreadingHTTPServer((host, port), H).serve_forever()
