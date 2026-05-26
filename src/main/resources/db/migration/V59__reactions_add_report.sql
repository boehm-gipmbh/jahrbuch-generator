ALTER TABLE reactions DROP CONSTRAINT IF EXISTS reactions_reaction_type_check;
ALTER TABLE reactions ADD CONSTRAINT reactions_reaction_type_check
    CHECK (reaction_type IN ('LIKE', 'VOTE', 'DISLIKE', 'REPORT'));
