ALTER TABLE reactions DROP CONSTRAINT IF EXISTS reactions_target_type_check;
ALTER TABLE reactions ADD CONSTRAINT reactions_target_type_check
    CHECK (target_type IN ('BILD', 'TEXT', 'VIDEO'));
