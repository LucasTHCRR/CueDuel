# Cue Duel lobby server

A small list of open games. Hosts register, the app shows the list. The game itself
runs directly between the two phones, and passwords are checked on the host phone.

No dependencies, Python 3 only.

```
TRUST_PROXY=1 PORT=8787 python3 server.py
```

Put it behind HTTPS (nginx example):

```
location /cueduel/api/ {
    proxy_pass http://127.0.0.1:8787/;
    proxy_set_header X-Forwarded-For $remote_addr;
}
```

The app uses `https://paperstream.dev/cueduel/api/v1` (`LOBBY_URL` in `scripts/net.gd`).

API:

- `GET /v1/lobbies` list
- `POST /v1/lobbies` register `{name, port, locked, version}` returns `{id, token}`
- `POST /v1/lobbies/<id>/ping` `{token, players}` every 15 s, entries expire after 45 s
- `POST /v1/lobbies/<id>/close` `{token}`
