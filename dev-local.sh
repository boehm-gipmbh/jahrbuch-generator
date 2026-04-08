#!/bin/bash
set -e

export FREE_DATABASE_URL="postgresql://postgres:postgres@localhost:5432/postgres"
export MIGRATIONS_DIR="$(dirname "$0")/src/main/resources/db/migration"
export DEV_DB_USERNAME=postgres
export DEV_DB_PASSWORD=postgres

# Aus .env laden falls vorhanden
if [ -f "$(dirname "$0")/.env" ]; then
  export $(grep -v '^#' "$(dirname "$0")/.env" | xargs)
fi

echo "=== Starte lokale DB-Migration ==="
bash "$(dirname "$0")/migrate.sh"

echo "=== Seed-Daten einspielen (falls noch nicht vorhanden) ==="
psql "$FREE_DATABASE_URL" -f "$(dirname "$0")/src/main/resources/import-dev.sql"

echo "=== Starte Quarkus (local) ==="
./mvnw compile quarkus:dev -Dquarkus.profile=local
