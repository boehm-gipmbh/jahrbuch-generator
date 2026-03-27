#!/bin/bash
# =============================================================================
# run-e2e.sh — Playwright E2E-Tests vollautomatisch starten
# Startet PostgreSQL (Docker), Backend, Frontend, führt Tests aus, räumt auf.
# =============================================================================
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
E2E="$ROOT/src/main/frontend/e2e"

PG_CONTAINER="postgres-e2e"
BACKEND_LOG="$ROOT/target/e2e-backend.log"
FRONTEND_LOG="$ROOT/target/e2e-frontend.log"
BACKEND_PID=""
FRONTEND_PID=""

# --- Farben ---
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()    { echo -e "${GREEN}[e2e]${NC} $*"; }
warn()    { echo -e "${YELLOW}[e2e]${NC} $*"; }
error()   { echo -e "${RED}[e2e]${NC} $*"; }

# --- Cleanup ---
cleanup() {
    info "Cleanup …"
    [ -n "$BACKEND_PID" ]  && kill "$BACKEND_PID"  2>/dev/null || true
    [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null || true
    docker stop "$PG_CONTAINER" 2>/dev/null || true
}
trap cleanup EXIT

# --- Warte bis URL antwortet ---
wait_for_url() {
    local url="$1" label="$2" max="${3:-60}"
    info "Warte auf $label ($url) …"
    for i in $(seq 1 "$max"); do
        if curl -sf "$url" -o /dev/null 2>/dev/null; then
            info "$label bereit."
            return 0
        fi
        sleep 1
    done
    error "$label nicht erreichbar nach ${max}s. Log:"
    [ -f "$BACKEND_LOG" ]  && tail -20 "$BACKEND_LOG"
    [ -f "$FRONTEND_LOG" ] && tail -20 "$FRONTEND_LOG"
    exit 1
}

# --- Warte bis PostgreSQL antwortet ---
wait_for_pg() {
    info "Warte auf PostgreSQL …"
    for i in $(seq 1 30); do
        if PGPASSWORD=postgres psql -h localhost -p 5432 -U postgres -d postgres \
               -c "SELECT 1" -o /dev/null 2>/dev/null; then
            info "PostgreSQL bereit."
            return 0
        fi
        sleep 1
    done
    error "PostgreSQL nicht bereit nach 30s."
    exit 1
}

mkdir -p "$ROOT/target"

# =============================================================================
# 0. Prüfen ob alle Test-Bilder vorhanden sind
# =============================================================================
E2E_BILDER=(
    "e2e-test-bild-1.jpg"
    "e2e-test-bild-2.jpg"
    "e2e-test-bild-3.jpg"
    "e2e-test-bild-4.jpg"
    "e2e-test-bild-5.jpg"
)
mkdir -p "$ROOT/captures"
for f in "${E2E_BILDER[@]}"; do
    if [ ! -f "$ROOT/captures/$f" ]; then
        if command -v convert &>/dev/null; then
            convert -size 400x300 "xc:#$(openssl rand -hex 3)" "$ROOT/captures/$f" 2>/dev/null \
                && info "Testbild erzeugt: $f" \
                || { error "convert fehlgeschlagen für $f"; exit 1; }
        else
            # Minimales JPEG ohne ImageMagick
            printf '\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00\xff\xdb\x00C\x00\x08\x06\x06\x07\x06\x05\x08\x07\x07\x07\t\t\x08\n\x0c\x14\r\x0c\x0b\x0b\x0c\x19\x12\x13\x0f\x14\x1d\x1a\x1f\x1e\x1d\x1a\x1c\x1c $.\' ",#\x1c\x1c(7),01444\x1f'"'"'9=82<.342\x1edL\t\x14 ##=\x19\x13=8\x1f\x1c\x1c\x1c\x1f\x1b\x1c\x1c\xff\xd9' > "$ROOT/captures/$f"
            info "Testbild (minimal) erzeugt: $f"
        fi
    fi
done
info "Alle Testbilder vorhanden."

# =============================================================================
# 1. PostgreSQL starten (Docker)
# =============================================================================
if docker ps --format '{{.Names}}' | grep -q "^${PG_CONTAINER}$"; then
    warn "PostgreSQL-Container '$PG_CONTAINER' läuft bereits."
else
    info "Starte PostgreSQL-Docker …"
    docker run -d --rm --name "$PG_CONTAINER" \
        -e POSTGRES_USER=postgres \
        -e POSTGRES_PASSWORD=postgres \
        -p 5432:5432 \
        postgres:latest
fi
wait_for_pg

# =============================================================================
# 2. Flyway-Migration
# =============================================================================
info "Führe DB-Migration aus …"
export FREE_DATABASE_URL="postgresql://postgres:postgres@localhost:5432/postgres"
export MIGRATIONS_DIR="$ROOT/src/main/resources/db/migration"
bash "$ROOT/migrate.sh"

# =============================================================================
# 3. Backend starten (Quarkus local)
# =============================================================================
info "Starte Backend …"
export DEV_DB_USERNAME=postgres
export DEV_DB_PASSWORD=postgres
"$ROOT/mvnw" compile quarkus:dev -Dquarkus.profile=local \
    -Dquarkus.log.console.enable=true \
    > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!
wait_for_url "http://localhost:8080/" "Backend" 120

# =============================================================================
# 4. Frontend starten (React Dev-Server)
# =============================================================================
info "Starte Frontend …"
cd "$ROOT/src/main/frontend"
npm start > "$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
wait_for_url "http://localhost:3000" "Frontend" 90
cd "$ROOT"

# =============================================================================
# 5. Playwright Tests ausführen
# =============================================================================
info "Starte Playwright Tests …"
cd "$E2E"
npx playwright test "$@"
EXIT_CODE=$?

info "Tests abgeschlossen (Exit $EXIT_CODE)."
echo ""
echo "  Report öffnen:  cd src/main/frontend/e2e && npx playwright show-report"
echo ""
exit $EXIT_CODE
