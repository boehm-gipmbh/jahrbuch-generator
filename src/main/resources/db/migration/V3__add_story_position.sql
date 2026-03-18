-- story_position: Position innerhalb der gemischten Story-Ansicht
-- Unabhängig von position (Einzel-Listen-Sortierung)

ALTER TABLE bilder ADD COLUMN story_position INTEGER NOT NULL DEFAULT 0;
ALTER TABLE texte ADD COLUMN story_position INTEGER NOT NULL DEFAULT 0;

-- Initialisierung: Texte bekommen gerade Positionen (0, 2, 4...),
-- Bilder ungerade (1, 3, 5...) — alternierend pro Story
WITH ranked_texte AS (
    SELECT id,
           (ROW_NUMBER() OVER (PARTITION BY story_id ORDER BY COALESCE(position, 0), id) - 1) * 2 AS sp
    FROM texte
    WHERE story_id IS NOT NULL
)
UPDATE texte SET story_position = ranked_texte.sp
FROM ranked_texte
WHERE texte.id = ranked_texte.id;

WITH ranked_bilder AS (
    SELECT id,
           (ROW_NUMBER() OVER (PARTITION BY story_id ORDER BY COALESCE(position, 0), id) - 1) * 2 + 1 AS sp
    FROM bilder
    WHERE story_id IS NOT NULL
)
UPDATE bilder SET story_position = ranked_bilder.sp
FROM ranked_bilder
WHERE bilder.id = ranked_bilder.id;