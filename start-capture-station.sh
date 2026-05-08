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

# ── Token: lokale Datei hat Vorrang vor application-station.properties ──────

EXTRA_ARGS=""
if [ -f "$SCRIPT_DIR/.station-token" ]; then
    TOKEN=$(cat "$SCRIPT_DIR/.station-token" | tr -d '[:space:]')
    echo "=== Token aus .station-token geladen ==="
    EXTRA_ARGS="-Djahrbuch.fotobox.token=$TOKEN"
fi

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
