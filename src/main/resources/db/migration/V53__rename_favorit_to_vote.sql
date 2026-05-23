UPDATE reactions SET reaction_type = 'VOTE' WHERE reaction_type = 'FAVORIT';

ALTER TABLE reactions DROP CONSTRAINT reactions_reaction_type_check;
ALTER TABLE reactions ADD CONSTRAINT reactions_reaction_type_check
    CHECK (reaction_type IN ('LIKE', 'VOTE'));
