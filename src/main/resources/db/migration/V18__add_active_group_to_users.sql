-- Aktive Gruppe des Users (für Gruppenauswahl im Frontend)
ALTER TABLE users
    ADD COLUMN active_group_id BIGINT REFERENCES gruppen(id) ON DELETE SET NULL;
