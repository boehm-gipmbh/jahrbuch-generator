ALTER TABLE bilder ADD COLUMN story_column INTEGER NOT NULL DEFAULT 0;
ALTER TABLE texte ADD COLUMN story_column INTEGER NOT NULL DEFAULT 0;

-- Distribute existing items alternately across two columns based on their global story_position
UPDATE bilder SET story_column = story_position % 2 WHERE story_id IS NOT NULL;
UPDATE texte SET story_column = story_position % 2 WHERE story_id IS NOT NULL;
