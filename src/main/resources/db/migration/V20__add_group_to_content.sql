-- Inhalte bekommen eine direkte Gruppen-Zugehörigkeit.
-- NULL = persönlicher Inhalt (ohne aktive Gruppe hochgeladen).
ALTER TABLE bilder ADD COLUMN group_id BIGINT REFERENCES gruppen(id) ON DELETE SET NULL;
ALTER TABLE texte  ADD COLUMN group_id BIGINT REFERENCES gruppen(id) ON DELETE SET NULL;
ALTER TABLE stories ADD COLUMN group_id BIGINT REFERENCES gruppen(id) ON DELETE SET NULL;
