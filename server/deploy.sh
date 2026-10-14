#!/usr/bin/env bash
# Deploys the server on this machine: tests, build, copy to ~/server/chatapp, restart.
# Runs from any folder: server/deploy.sh
set -euo pipefail

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="$HOME/server/chatapp"
# The database lives here, apart from the app copy: a deploy replaces the app and keeps the data.
DATA_DIR="$INSTALL_DIR/data"
# API_KEY for the service; the app's copy goes to chatapp.apiKey in the app's local.properties.
ENV_FILE="$INSTALL_DIR/server.env"
SERVICE=chatapp-server
# The ports of all servers on this machine are listed in ~/server/README.md.
PORT=8081

: "${JAVA_HOME:?JAVA_HOME must point to JDK 21: the server builds and runs on it}"

# --- 1. Tests and build ---

# A failed test stops the script here and leaves the running server alone.
cd "$SERVER_DIR"
./gradlew test installDist

# --- 2. The key and the systemd service ---

mkdir -p "$DATA_DIR"
# Made once: a new key on every deploy would lock out the app until it is rebuilt.
if [[ ! -f "$ENV_FILE" ]]; then
    (umask 077 && echo "API_KEY=$(head -c 24 /dev/urandom | od -An -tx1 | tr -d ' \n')" > "$ENV_FILE")
    echo "New API key in $ENV_FILE: put it into the app's local.properties as chatapp.apiKey"
fi

# systemd restarts the server after a crash and starts it after a reboot.
SERVICE_FILE="$HOME/.config/systemd/user/$SERVICE.service"
mkdir -p "$(dirname "$SERVICE_FILE")"
cat > "$SERVICE_FILE" <<UNIT
[Unit]
Description=ChatApp server

[Service]
Environment=JAVA_HOME=$JAVA_HOME
Environment=PORT=$PORT
Environment=DB_PATH=$DATA_DIR/chat.db
EnvironmentFile=$ENV_FILE
ExecStart=$INSTALL_DIR/app/bin/server
Restart=on-failure
RestartSec=5
# The JVM exits with 143 on SIGTERM; without this, every stop is logged as a failure.
SuccessExitStatus=143

[Install]
WantedBy=default.target
UNIT
systemctl --user daemon-reload

# --- 3. Replacing the running version ---

# Stop first, so the old version doesn't run from files being replaced.
systemctl --user stop "$SERVICE" || true
rm -rf "$INSTALL_DIR/app"
cp -r build/install/server "$INSTALL_DIR/app"
systemctl --user enable --now "$SERVICE"

# --- 4. Checks ---

# Without linger, user services don't start after a reboot until the user logs in.
if [[ "$(loginctl show-user "$USER" -p Linger --value)" != yes ]]; then
    echo "Warning: the server won't start after a reboot. Run once: sudo loginctl enable-linger $USER" >&2
fi

API_KEY="$(sed -n 's/^API_KEY=//p' "$ENV_FILE")"
# The first start fills an empty database, which takes a few seconds.
for _ in $(seq 60); do
    if curl -sf -H "X-Api-Key: $API_KEY" "http://localhost:$PORT/users" > /dev/null; then
        echo "Server is running on port $PORT"
        exit 0
    fi
    sleep 1
done
echo "Server didn't respond in 60 seconds. Logs: journalctl --user -u $SERVICE -n 50" >&2
exit 1
