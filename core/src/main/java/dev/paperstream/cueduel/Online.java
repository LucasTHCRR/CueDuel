package dev.paperstream.cueduel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

// Online-Spiel: ein Handy eröffnet, alles läuft über das Relay auf dem Lobby-Server
public final class Online implements WsClient.Listener {
    public interface Listener {
        void onMessage(JsonValue d);

        void onConnected();

        void onLost();

        void onFailed(String why);

        void onDenied();

        void onStatus();

        void onLobbies(List<Lobby> list);
    }

    public static final class Lobby {
        public String id, name, version;
        public boolean locked;
        public int players;
    }

    public interface Reply {
        void done(JsonValue data);
    }

    public String lobbyUrl = "https://paperstream.dev/cueduel/api/v1";
    public String relayUrl = "wss://paperstream.dev/cueduel/relay";
    public boolean host, active, authed, listFailed;
    public String lobbyId = "";
    public Listener listener;

    private String token = "", password = "", joinPassword = "", lobbyName = "";
    private boolean listed;
    private int players = 1;
    private WsClient ws;
    private String wsRole = "";
    private float hb, wsPing, joinTimeout = -1, reconnect = -1;
    private int session;

    public Online() {
        String l = System.getenv("CUEDUEL_LOBBY");
        String r = System.getenv("CUEDUEL_RELAY");
        if (l != null && !l.isEmpty() && r != null && !r.isEmpty()) {
            lobbyUrl = l;
            relayUrl = r;
        }
    }

    public void hostGame(String name, String pw, boolean show) {
        stop();
        password = pw;
        lobbyName = name;
        listed = show;
        players = 1;
        host = true;
        active = true;
        listFailed = false;
        final int my = session;
        String body = "{\"name\":" + quote(name) + ",\"port\":24680,\"locked\":" + !pw.isEmpty()
            + ",\"open\":false,\"listed\":" + show + ",\"lans\":[],\"version\":" + quote(Game.VERSION) + "}";
        request("POST", "/lobbies", body, data -> {
            if (my != session || !active || !host) return;
            if (data != null && data.has("id")) {
                lobbyId = data.getString("id");
                token = data.getString("token", "");
                hb = 0;
                wsConnect("host", lobbyId, token);
            } else {
                listFailed = true;
            }
            listener.onStatus();
        });
        listener.onStatus();
    }

    public void join(String code, String pw) {
        stop();
        joinPassword = pw;
        host = false;
        active = true;
        joinTimeout = 12;
        wsConnect("guest", code.trim().toLowerCase(), "");
    }

    public void stop() {
        session++;
        joinTimeout = -1;
        reconnect = -1;
        if (!lobbyId.isEmpty() && host) {
            request("POST", "/lobbies/" + lobbyId + "/close", "{\"token\":" + quote(token) + "}", data -> {
            });
        }
        lobbyId = "";
        token = "";
        authed = false;
        if (ws != null) {
            ws.close();
            ws = null;
        }
        active = false;
        host = false;
    }

    public void send(String json) {
        if (active && ws != null && ws.isOpen()) ws.send("{\"t\":\"data\",\"d\":" + json + "}");
    }

    public void update(float dt) {
        if (!active) return;
        if (ws != null) {
            wsPing += dt;
            if (wsPing > 20) {
                wsPing = 0;
                ws.ping();
            }
        }
        if (host && !lobbyId.isEmpty()) {
            hb += dt;
            if (hb > 15) ping();
        }
        if (joinTimeout > 0) {
            joinTimeout -= dt;
            if (joinTimeout <= 0) {
                joinTimeout = -1;
                fail("Could not connect");
            }
        }
        if (reconnect > 0) {
            reconnect -= dt;
            if (reconnect <= 0) {
                reconnect = -1;
                if (host && ws == null && !authed && !lobbyId.isEmpty()) wsConnect("host", lobbyId, token);
            }
        }
    }

    private void fail(String why) {
        stop();
        listener.onFailed(why);
    }

    private void ping() {
        hb = 0;
        request("POST", "/lobbies/" + lobbyId + "/ping", "{\"token\":" + quote(token) + ",\"players\":" + players + "}",
            data -> {
            });
    }

    // ---------- relay ----------

    private void wsConnect(String role, String id, String tok) {
        if (ws != null) ws.close();
        wsRole = role;
        wsPing = 0;
        final String hello = "{\"t\":\"" + role + "\",\"id\":" + quote(id) + ",\"token\":" + quote(tok) + "}";
        ws = new WsClient(relayUrl, new WsClient.Listener() {
            @Override
            public void onOpen(WsClient c) {
                c.send(hello);
            }

            @Override
            public void onText(WsClient c, String text) {
                Online.this.onText(c, text);
            }

            @Override
            public void onClosed(WsClient c) {
                Online.this.onClosed(c);
            }
        });
        ws.connect();
    }

    @Override
    public void onOpen(WsClient c) {
    }

    @Override
    public void onText(final WsClient c, final String text) {
        Gdx.app.postRunnable(() -> {
            if (c != ws) return;
            JsonValue m;
            try {
                m = new JsonReader().parse(text);
            } catch (Exception e) {
                return;
            }
            if (m == null || !m.isObject()) return;
            switch (m.getString("t", "")) {
                case "ok":
                    if (wsRole.equals("guest")) {
                        joinTimeout = 15;
                        ws.send("{\"t\":\"data\",\"d\":{\"t\":\"hello\",\"pw\":" + quote(hash(joinPassword)) + ",\"v\":"
                            + quote(Game.VERSION) + "}}");
                        listener.onStatus();
                    }
                    break;
                case "error":
                    if (wsRole.equals("guest")) fail(m.getString("why", "Could not connect"));
                    break;
                case "bye":
                    if (authed) {
                        stop();
                        listener.onLost();
                    }
                    break;
                case "data":
                    JsonValue d = m.get("d");
                    if (d != null && d.isObject()) deliver(d);
                    break;
                default:
                    break;
            }
        });
    }

    private void deliver(JsonValue d) {
        String t = d.getString("t", "");
        if (t.equals("hello")) {
            if (!host || authed) return;
            if (hash(password).equals(d.getString("pw", ""))) {
                authed = true;
                players = 2;
                ping();
                listener.onConnected();
            } else {
                send("{\"t\":\"deny\"}");
            }
        } else if (t.equals("deny")) {
            if (!host) {
                stop();
                listener.onDenied();
            }
        } else {
            if (!host && !authed && t.equals("rack")) {
                authed = true;
                joinTimeout = -1;
            }
            if (authed) listener.onMessage(d);
        }
    }

    @Override
    public void onClosed(final WsClient c) {
        Gdx.app.postRunnable(() -> {
            if (c != ws) return;
            ws = null;
            if (!active) return;
            if (authed) {
                stop();
                listener.onLost();
            } else if (!host) {
                fail("Could not connect");
            } else if (!lobbyId.isEmpty()) {
                reconnect = 2;
            }
        });
    }

    public static String hash(String pw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(("cueduel" + pw).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(Character.forDigit(x >> 4 & 15, 16)).append(Character.forDigit(x & 15, 16));
            return b.toString();
        } catch (Exception e) {
            return pw;
        }
    }

    public static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 32) b.append(' ');
            else b.append(c);
        }
        return b.append('"').toString();
    }

    // ---------- lobby list ----------

    public void fetchLobbies() {
        request("GET", "/lobbies", null, data -> {
            if (data == null || !data.isArray()) {
                listener.onLobbies(null);
                return;
            }
            List<Lobby> out = new ArrayList<>();
            for (JsonValue v = data.child; v != null; v = v.next) {
                Lobby l = new Lobby();
                l.id = v.getString("id", "");
                l.name = v.getString("name", "Cue Duel");
                l.version = v.getString("version", "");
                l.locked = v.getBoolean("locked", false);
                l.players = v.getInt("players", 1);
                // Spiele der alten App sind nicht kompatibel
                if (l.version.startsWith("0.") || l.version.startsWith("1.") || l.version.isEmpty()) continue;
                if (l.id.equals(lobbyId)) continue;
                out.add(l);
            }
            listener.onLobbies(out);
        });
    }

    private void request(String method, String path, String body, final Reply cb) {
        Net.HttpRequest r = new Net.HttpRequest(method);
        r.setUrl(lobbyUrl + path);
        r.setTimeOut(8000);
        if (body != null) {
            r.setHeader("Content-Type", "application/json");
            r.setContent(body);
        }
        Gdx.net.sendHttpRequest(r, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse resp) {
                JsonValue data = null;
                try {
                    int code = resp.getStatus().getStatusCode();
                    String text = resp.getResultAsString();
                    if (code >= 200 && code < 300) data = new JsonReader().parse(text);
                } catch (Exception ignored) {
                }
                final JsonValue d = data;
                Gdx.app.postRunnable(() -> cb.done(d));
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> cb.done(null));
            }

            @Override
            public void cancelled() {
                Gdx.app.postRunnable(() -> cb.done(null));
            }
        });
    }
}
