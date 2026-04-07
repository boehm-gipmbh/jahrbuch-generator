ALTER TABLE users
    ADD COLUMN used_invitation_id BIGINT REFERENCES invitation_tokens(id) ON DELETE SET NULL;
