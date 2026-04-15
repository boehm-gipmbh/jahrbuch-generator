#!/bin/bash
# migrate_bilder_gruppenverzeichnisse.sh
#
# Verschiebt bestehende Bilder (und ihre Thumbnails) in Gruppenunterverzeichnisse
# und aktualisiert die Pfade in der Datenbank.
#
# Idempotent: Bilder mit Pfad /gruppen/... oder /ungrouped/... werden übersprungen.
#
# Verwendung:
#   ./migrate_bilder_gruppenverzeichnisse.sh <CAPTURES_PATH> <DB_URL>
#
# Beispiele:
#   ./migrate_bilder_gruppenverzeichnisse.sh /data/captures "postgres://postgres:postgres@localhost:5433/postgres"
#   ./migrate_bilder_gruppenverzeichnisse.sh ./captures    "postgres://postgres:pw@localhost:5433/postgres"

set -e

CAPTURES_PATH="${1:?Pfad zu captures fehlt, z.B. /data/captures}"
DB_URL="${2:?DB-URL fehlt, z.B. postgres://user:pw@host:5432/db}"
SKIP_DUMP="${3:-}"

# Trailing slash entfernen
CAPTURES_PATH="${CAPTURES_PATH%/}"

echo "=== Bilder-Migration zu Gruppenverzeichnissen ==="
echo "Captures: $CAPTURES_PATH"
echo "DB:       $DB_URL"
echo ""

# DB-Dump als Backup vor der Migration (überspringen mit --skip-dump)
if [ "$SKIP_DUMP" = "--skip-dump" ]; then
    echo "DB-Dump übersprungen (--skip-dump gesetzt)."
else
    DUMP_FILE="pre_migration_dump_$(date +%Y%m%d_%H%M%S).sql"
    echo "Erstelle DB-Dump: $DUMP_FILE ..."
    pg_dump "$DB_URL" > "$DUMP_FILE"
    echo "Dump erstellt: $DUMP_FILE"
fi
echo ""

# Zu migrierende Bilder abfragen: id | alter_pfad | neuer_pfad
QUERY="
SELECT
    b.id,
    b.pfad,
    CASE
        WHEN u.active_group_id IS NOT NULL
            THEN '/gruppen/' || u.active_group_id || b.pfad
        ELSE '/ungrouped' || b.pfad
    END AS new_pfad
FROM bilder b
JOIN users u ON b.user_id = u.id
WHERE b.pfad NOT LIKE '/gruppen/%'
  AND b.pfad NOT LIKE '/ungrouped/%'
ORDER BY b.id;
"

total=0
moved=0
missing=0
errors=0

while IFS='|' read -r id old_pfad new_pfad; do
    [[ -z "$id" ]] && continue
    total=$((total + 1))

    old_file="$CAPTURES_PATH$old_pfad"
    new_file="$CAPTURES_PATH$new_pfad"
    new_dir="$(dirname "$new_file")"

    # Verzeichnis anlegen
    mkdir -p "$new_dir"

    # Hauptdatei verschieben
    if [ -f "$old_file" ]; then
        mv "$old_file" "$new_file"
        moved=$((moved + 1))
        echo "OK  $old_pfad → $new_pfad"
    else
        missing=$((missing + 1))
        echo "--- Datei fehlt auf Disk: $old_file (DB wird trotzdem aktualisiert)"
    fi

    # Thumbnail verschieben (Pattern: basename_thumb.jpg)
    base="${old_pfad%.*}"
    old_thumb="$CAPTURES_PATH${base}_thumb.jpg"
    new_base="${new_pfad%.*}"
    new_thumb="$CAPTURES_PATH${new_base}_thumb.jpg"
    if [ -f "$old_thumb" ]; then
        mv "$old_thumb" "$new_thumb"
        echo "    Thumbnail verschoben"
    fi

    # DB-Pfad aktualisieren (version +1 für Hibernate optimistic locking)
    psql "$DB_URL" -q -c "UPDATE bilder SET pfad = '$new_pfad', version = version + 1 WHERE id = $id;" || {
        echo "FEHLER beim DB-Update für id=$id"
        errors=$((errors + 1))
    }

done < <(psql "$DB_URL" -t -A -F'|' -c "$QUERY")

echo ""
echo "=== Ergebnis ==="
echo "Gesamt:         $total"
echo "Verschoben:     $moved"
echo "Datei fehlte:   $missing"
echo "DB-Fehler:      $errors"
