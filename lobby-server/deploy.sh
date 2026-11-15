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
Description=Cue Duel lobby list and relay
After=network-online.target
Wants=network-online.target
StartLimitIntervalSec=0

[Service]
Environment=PORT=5091
Environment=WS_PORT=5092
Environment=TRUST_PROXY=1
Environment=PYTHONUNBUFFERED=1
ExecStart=/usr/bin/python3 /opt/cueduel-lobby/server.py
DynamicUser=yes
Restart=always
RestartSec=3
NoNewPrivileges=true
ProtectSystem=strict
ProtectHome=true
PrivateTmp=true
PrivateDevices=true
ProtectKernelTunables=true
ProtectControlGroups=true
RestrictAddressFamilies=AF_INET AF_INET6
MemoryMax=200M
LimitNOFILE=4096

[Install]
WantedBy=multi-user.target
U
systemctl daemon-reload
systemctl enable paperstream-cueduel-lobby
systemctl restart paperstream-cueduel-lobby

# eigene Datei, damit die Route einen Tausch der Seiten-Conf übersteht
cat > /etc/nginx/snippets/paperstream-cueduel.conf <<'N'
location /cueduel/api/ {
    client_max_body_size 4k;
    proxy_pass http://127.0.0.1:5091/;
    proxy_set_header X-Forwarded-For $remote_addr;
}
location = /cueduel/relay {
    proxy_pass http://127.0.0.1:5092;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
N
F=/etc/nginx/sites-enabled/paperstream.dev.conf
cp -a "$F" /root/paperstream.dev.conf.bak-cueduel
grep -q paperstream-cueduel.conf "$F" || sed -i 's|^    include snippets/paperstream-legal.conf;|&\n    include snippets/paperstream-cueduel.conf;|' "$F"
if grep -q paperstream-cueduel.conf "$F" && nginx -t; then
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
