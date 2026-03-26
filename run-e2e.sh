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
REQUIRED_BILDER=(
    "20250818_135428_6d60d04b-94d3-4036-9c06-9c11316abe40.jpg"
    "20250818_135510_f7b0be4e-1bbc-4ea0-81be-9d172acfa370.jpg"
    "20250818_135703_61c3b02d-4fc0-4870-85b9-167ba70cae40.jpg"
    "20250818_135813_b7957ba8-9fdc-4054-928d-bcf086d7377c.jpg"
    "20250818_140105_a082e36a-4885-48bf-8945-d63fbe15b4ca.jpg"
)
MISSING=0
for f in "${REQUIRED_BILDER[@]}"; do
    if [ ! -f "$ROOT/captures/$f" ]; then
        error "Fehlendes Testbild: captures/$f"
        MISSING=1
    fi
done
if [ "$MISSING" -eq 1 ]; then
    error "Bitte fehlende Bilder in captures/ bereitstellen und erneut starten."
    exit 1
fi
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
