ALTER TABLE stories ADD COLUMN IF NOT EXISTS order_position INTEGER NOT NULL DEFAULT 0;

UPDATE stories SET order_position = sub.rn
FROM (
    SELECT id,
        ROW_NUMBER() OVER (
            PARTITION BY COALESCE(stories.group_id::text, 'u' || stories.user_id::text)
            ORDER BY created
        ) - 1 AS rn
    FROM stories
) sub
WHERE stories.id = sub.id;
