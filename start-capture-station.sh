#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ── Voraussetzungen prüfen ──────────────────────────────────────────────────

if ! command -v gphoto2 &>/dev/null; then
    echo "FEHLER: gphoto2 nicht gefunden. Bitte installieren: sudo apt-get install gphoto2"
    exit 1
fi

if ! command -v java &>/dev/null; then
    echo "FEHLER: Java nicht gefunden. Bitte installieren: sudo apt-get install temurin-21-jdk"
    exit 1
fi

# ── Kamera prüfen ──────────────────────────────────────────────────────────

echo "=== Kamera-Erkennung ==="
gphoto2 --auto-detect
echo ""

# ── gphoto2-java lokal installieren (einmalig) ─────────────────────────────

echo "=== gphoto2-java installieren ==="
./mvnw install:install-file \
    -Dfile=libs/gphoto2-java-1.5-SNAPSHOT.jar \
    -DgroupId=org.gphoto \
    -DartifactId=gphoto2-java \
    -Dversion=1.5-SNAPSHOT \
    -Dpackaging=jar \
    -B -q
echo "OK"

# ── Token laden (Pflicht) ───────────────────────────────────────────────────
# Priorität: 1. .station-token Datei  2. Umgebungsvariable FOTOBOX_TOKEN

TOKEN=""
if [ -f "$SCRIPT_DIR/.station-token" ]; then
    TOKEN=$(cat "$SCRIPT_DIR/.station-token" | tr -d '[:space:]')
    echo "=== Token aus .station-token geladen ==="
elif [ -n "${FOTOBOX_TOKEN:-}" ]; then
    TOKEN="$FOTOBOX_TOKEN"
    echo "=== Token aus Umgebungsvariable geladen ==="
fi

if [ -z "$TOKEN" ]; then
    echo ""
    echo "FEHLER: Kein Fotobox-Token gefunden."
    echo "Bitte die Datei .station-token im Projektverzeichnis anlegen."
    echo ""
    exit 1
fi

EXTRA_ARGS="-Djahrbuch.fotobox.token=$TOKEN"

# ── Captures-Verzeichnis anlegen ────────────────────────────────────────────

CAPTURES_DIR="${CAPTURES_PATH:-$HOME/captures}"
mkdir -p "$CAPTURES_DIR"
echo "=== Captures-Pfad: $CAPTURES_DIR ==="

# ── Starten ─────────────────────────────────────────────────────────────────

echo ""
echo "=== Capture Station startet auf http://localhost:8080 ==="
echo ""

./mvnw compile quarkus:dev \
    -Dquarkus.profile=station \
    -Djahrbuch.captures.path="$CAPTURES_DIR/" \
    $EXTRA_ARGS
