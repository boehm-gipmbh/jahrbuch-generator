#!/bin/bash
set -e

# FREE_DATABASE_URL: postgresql://user:pass@host:5432/db (direkt psql-kompatibel)
PSQL_URL="$FREE_DATABASE_URL"

echo "=== DB Migration ==="
echo "  URL: ${PSQL_URL%%:*}://***"

# Tracking-Tabelle anlegen
psql "$PSQL_URL" -c "
CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT NOW()
);"

# Baseline: falls Tabellen schon existieren (vorhandene DB), V1 als angewendet markieren
psql "$PSQL_URL" -c "
INSERT INTO schema_migrations (version)
SELECT 'V1__initial_schema'
WHERE EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'bilder'
)
AND NOT EXISTS (
    SELECT 1 FROM schema_migrations WHERE version = 'V1__initial_schema'
);"

# Alle V*.sql Dateien in Reihenfolge anwenden
MIGRATIONS_DIR="${MIGRATIONS_DIR:-/deployments/migrations}"
for sql_file in $(ls "$MIGRATIONS_DIR"/V*.sql | sort -V); do
    version=$(basename "$sql_file" .sql)
    if psql "$PSQL_URL" -tAc "SELECT 1 FROM schema_migrations WHERE version = '$version'" | grep -q 1; then
        echo "  [skip] $version"
    else
        echo "  [run]  $version"
        psql "$PSQL_URL" -v ON_ERROR_STOP=1 -f "$sql_file"
        psql "$PSQL_URL" -c "INSERT INTO schema_migrations (version) VALUES ('$version');"
        echo "  [done] $version"
    fi
done

echo "=== Migration abgeschlossen ==="
