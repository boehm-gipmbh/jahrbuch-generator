CREATE TABLE cluster (id BIGSERIAL PRIMARY KEY);

ALTER TABLE bilder ADD COLUMN cluster_id BIGINT REFERENCES cluster(id) ON DELETE SET NULL;
ALTER TABLE texte  ADD COLUMN cluster_id BIGINT REFERENCES cluster(id) ON DELETE SET NULL;

CREATE INDEX idx_bilder_cluster ON bilder(cluster_id) WHERE cluster_id IS NOT NULL;
CREATE INDEX idx_texte_cluster  ON texte(cluster_id)  WHERE cluster_id IS NOT NULL;

-- Migrate existing text_bild_link pairs into clusters
DO $$
DECLARE
    r RECORD;
    cid BIGINT;
    ca  BIGINT;
    cb  BIGINT;
BEGIN
    FOR r IN SELECT text_id, bild_id FROM text_bild_link LOOP
        SELECT cluster_id INTO ca FROM texte  WHERE id = r.text_id;
        SELECT cluster_id INTO cb FROM bilder WHERE id = r.bild_id;
        IF ca IS NOT NULL THEN
            cid := ca;
        ELSIF cb IS NOT NULL THEN
            cid := cb;
        ELSE
            INSERT INTO cluster DEFAULT VALUES RETURNING id INTO cid;
        END IF;
        UPDATE texte  SET cluster_id = cid WHERE id = r.text_id  AND cluster_id IS NULL;
        UPDATE bilder SET cluster_id = cid WHERE id = r.bild_id AND cluster_id IS NULL;
    END LOOP;
END $$;

DROP TABLE text_bild_link;
