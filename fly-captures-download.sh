#!/bin/bash

# Token aus Datei lesen
TOKEN_FILE="jahrbuch-generator.token.ssh"
APP_NAME="jahrbuch-generator"  # Füge den App-Namen hier ein

# Prüfen, ob die Token-Datei existiert
if [ ! -f "$TOKEN_FILE" ]; then
  echo "Fehler: Token-Datei '$TOKEN_FILE' nicht gefunden."
  exit 1
fi

# Absoluten Pfad zum Token-File ermitteln
TOKEN_FILE_ABS="$(readlink -f "$TOKEN_FILE")"

# Zielverzeichnis für heruntergeladene Dateien (optionales Argument, Standard: letztes captures_* oder neues mit Timestamp)
_LAST_DIR=$(ls -dt ./captures_* 2>/dev/null | head -1)
DOWNLOAD_DIR="${1:-${_LAST_DIR:-./captures_$(date +%Y%m%d_%H%M%S)}}"
# Optionales Remote-Ziel für rsync, z.B. root@178.105.56.161:/data/captures
REMOTE_TARGET="${2:-}"
FLYCTL="/home/dboehm/.fly/bin/flyctl"

# Verzeichnis erstellen, ignoriere Fehler wenn es bereits existiert
mkdir -p "$DOWNLOAD_DIR" 2>/dev/null

echo "Dateiliste von /data/captures ermitteln..."

# Temporäre Datei für die Dateiliste
FILE_LIST=$(mktemp)

FLY_API_TOKEN=$(cat "$TOKEN_FILE_ABS") $FLYCTL ssh console -a "$APP_NAME" -C "find /data/captures -type f" > "$FILE_LIST"

if [ $? -ne 0 ] || [ ! -s "$FILE_LIST" ]; then
  echo "Fehler: Konnte keine Verbindung zu Fly.io herstellen oder keine Dateien gefunden."
  rm -f "$FILE_LIST"
  exit 1
fi

# Anzahl der Dateien ausgeben
COUNT=$(wc -l < "$FILE_LIST")
echo "Gefunden: $COUNT Dateien"

# Ping-Loop starten damit die App nicht einschläft
while true; do curl -s "https://$APP_NAME.fly.dev" > /dev/null 2>&1; sleep 20; done &
PING_PID=$!

# Zähler für Fortschrittsanzeige
COUNTER=0

# Durch die Dateiliste iterieren und Dateien herunterladen
while read -r FILE_PATH; do
  # Relativen Pfad unterhalb /data/captures beibehalten
  RELATIVE_PATH="${FILE_PATH#/data/captures/}"
  TARGET_DIR="$DOWNLOAD_DIR/$(dirname "$RELATIVE_PATH")"

  # Fortschritt anzeigen
  COUNTER=$((COUNTER + 1))
  echo "[$COUNTER/$COUNT] Lade $RELATIVE_PATH herunter..."

  # Zielverzeichnis anlegen
  mkdir -p "$TARGET_DIR"

  # Bereits vorhandene und valide Dateien überspringen
  LOCAL_FILE="$TARGET_DIR/$(basename "$RELATIVE_PATH")"
  if [ -f "$LOCAL_FILE" ] && [ -s "$LOCAL_FILE" ]; then
    CORRUPT=false
    case "$LOCAL_FILE" in
      *.jpg|*.jpeg|*.JPG|*.JPEG)
        if command -v jpeginfo &>/dev/null; then
          jpeginfo -c "$LOCAL_FILE" &>/dev/null || CORRUPT=true
        fi
        ;;
    esac
    if [ "$CORRUPT" = true ]; then
      echo "  neu herunterladen (truncated/korrupt)"
      rm -f "$LOCAL_FILE"
    else
      echo "  übersprungen (valide)"
      continue
    fi
  fi

  # Datei herunterladen
  (cd "$TARGET_DIR" && FLY_API_TOKEN=$(cat "$TOKEN_FILE_ABS") $FLYCTL -a "$APP_NAME" ssh sftp get "$FILE_PATH")

  if [ $? -eq 0 ]; then
    echo "✓ $RELATIVE_PATH erfolgreich heruntergeladen"
  else
    echo "✗ Fehler beim Herunterladen von $RELATIVE_PATH"
  fi
done < "$FILE_LIST"

# Ping-Loop beenden
kill $PING_PID 2>/dev/null

# Temporäre Datei löschen
rm -f "$FILE_LIST"

echo "Download abgeschlossen. Alle Dateien wurden nach $DOWNLOAD_DIR gespeichert."