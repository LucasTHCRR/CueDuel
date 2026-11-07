#!/bin/bash
# Installiert die Lobby-Liste auf dem Server von paperstream.dev (Port 5091, nginx unter /cueduel/api/)
set -euo pipefail
cd "$(dirname "$0")"
KEY=/home/lucasthcr/.ssh/id_ed25519_45-133-74-209
HOST=root@45.133.74.209
SSH="ssh -i $KEY -o BatchMode=yes $HOST"

$SSH 'mkdir -p /opt/cueduel-lobby'
scp -q -i "$KEY" server.py "$HOST:/opt/cueduel-lobby/server.py"
$SSH 'bash -s' <<'REMOTE'
set -e
cat > /etc/systemd/system/paperstream-cueduel-lobby.service <<'U'
[Unit]
Description=Cue Duel lobby list
After=network.target

[Service]
Environment=PORT=5091
Environment=TRUST_PROXY=1
ExecStart=/usr/bin/python3 /opt/cueduel-lobby/server.py
User=nobody
Restart=on-failure
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true

[Install]
WantedBy=multi-user.target
U
systemctl daemon-reload
systemctl enable --now paperstream-cueduel-lobby
systemctl restart paperstream-cueduel-lobby

F=/etc/nginx/sites-enabled/paperstream.dev.conf
cp -a "$F" /root/paperstream.dev.conf.bak-cueduel
python3 - <<'P'
f = "/etc/nginx/sites-enabled/paperstream.dev.conf"
s = open(f).read()
blk = """    location /cueduel/api/ {
        client_max_body_size 4k;
        proxy_pass http://127.0.0.1:5091/;
        proxy_set_header X-Forwarded-For $remote_addr;
    }
"""
if "/cueduel/api/" not in s:
    s = s.replace("    location / { try_files", blk + "    location / { try_files", 1)
open(f, "w").write(s)
P
if nginx -t; then
  systemctl reload nginx
  echo "nginx reloaded"
else
  cp -a /root/paperstream.dev.conf.bak-cueduel "$F"
  echo "nginx test failed, old config restored"
  exit 1
fi
systemctl is-active paperstream-cueduel-lobby
REMOTE

python3 -I - <<'P'
import urllib.request as u
r = u.urlopen("https://paperstream.dev/cueduel/api/v1/lobbies", timeout=10)
print("list:", r.status, r.read(80))
P
