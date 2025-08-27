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

# Zielverzeichnis für heruntergeladene Dateien
DOWNLOAD_DIR="./captures"
FLYCTL="/home/dboehm/.fly/bin/flyctl"

# Verzeichnis erstellen, ignoriere Fehler wenn es bereits existiert
mkdir -p "$DOWNLOAD_DIR" 2>/dev/null

echo "Dateiliste von /data/captures ermitteln..."

# Temporäre Datei für die Dateiliste
FILE_LIST=$(mktemp)

# Den exakt funktionierenden Befehl verwenden (ohne -type f Filter)
FLY_API_TOKEN=$(cat "$TOKEN_FILE_ABS") $FLYCTL ssh sftp find /data/captures > "$FILE_LIST"

# Dateien aus der Liste filtern (Verzeichnisse ausschließen)
grep -v "/$" "$FILE_LIST" > "${FILE_LIST}.tmp" && mv "${FILE_LIST}.tmp" "$FILE_LIST"

# Fehlerbehandlung
if [ $? -ne 0 ] || [ ! -s "$FILE_LIST" ]; then
  echo "Fehler: Konnte keine Verbindung zu Fly.io herstellen oder keine Dateien gefunden."
  rm -f "$FILE_LIST"
  exit 1
fi

# Anzahl der Dateien ausgeben
COUNT=$(wc -l < "$FILE_LIST")
echo "Gefunden: $COUNT Dateien"

# Zähler für Fortschrittsanzeige
COUNTER=0

# Durch die Dateiliste iterieren und Dateien herunterladen
while read -r FILE_PATH; do
  # Dateiname ohne Pfad extrahieren
  FILE_NAME=$(basename "$FILE_PATH")

  # Fortschritt anzeigen
  COUNTER=$((COUNTER + 1))
  echo "[$COUNTER/$COUNT] Lade $FILE_NAME herunter..."

  # Datei herunterladen mit absoluten Pfad zum Token-File und App-Namen
  (cd "$DOWNLOAD_DIR" && FLY_API_TOKEN=$(cat "$TOKEN_FILE_ABS") $FLYCTL -a "$APP_NAME" ssh sftp get "$FILE_PATH")

  # Prüfen ob Download erfolgreich war
  if [ $? -eq 0 ]; then
    echo "✓ $FILE_NAME erfolgreich heruntergeladen"
  else
    echo "✗ Fehler beim Herunterladen von $FILE_NAME"
  fi
done < "$FILE_LIST"

# Temporäre Datei löschen
rm -f "$FILE_LIST"

echo "Download abgeschlossen. Alle Dateien wurden nach $DOWNLOAD_DIR gespeichert."