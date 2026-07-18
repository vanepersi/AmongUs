#!/usr/bin/env bash
# Deploy AmongUs to Genesi Club via the local FileZilla SFTP tunnel (127.0.0.1:2022).
# Credentials: set CLUB_SFTP_USER / CLUB_SFTP_PASS, or SSHPASS.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${1:-$ROOT/build/libs/AmongUs-1.0.0.jar}"
HOST="${CLUB_SFTP_HOST:-127.0.0.1}"
PORT="${CLUB_SFTP_PORT:-2022}"
USER="${CLUB_SFTP_USER:-jangyga.7074bd1d}"

if [[ ! -f "$JAR" ]]; then
  echo "Missing jar: $JAR" >&2
  exit 1
fi

if [[ -z "${SSHPASS:-${CLUB_SFTP_PASS:-}}" ]]; then
  echo "Set CLUB_SFTP_PASS or SSHPASS (Club SFTP password from FileZilla)." >&2
  exit 1
fi
export SSHPASS="${SSHPASS:-$CLUB_SFTP_PASS}"

echo "==> Uploading $(basename "$JAR") to Club ($USER@$HOST:$PORT)"
sshpass -e sftp -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no -P "$PORT" "$USER@$HOST" <<EOF
put $JAR plugins/AmongUs-1.0.0.jar
ls plugins/AmongUs*
EOF

echo "==> Done. Restart Club (or /plugman load) then set up arenas with /amongusadmin."
echo "    Data will live at plugins/GenesiCore/games/AmongUs/"
