ALTER TABLE reactions DROP CONSTRAINT IF EXISTS reactions_reaction_type_check;
UPDATE reactions SET reaction_type = 'VOTE' WHERE reaction_type = 'FAVORIT';
ALTER TABLE reactions ADD CONSTRAINT reactions_reaction_type_check
    CHECK (reaction_type IN ('LIKE', 'VOTE'));
