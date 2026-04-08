-- Many-to-many: User ↔ Gruppe
CREATE TABLE user_groups (
    user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_id BIGINT NOT NULL REFERENCES gruppen(id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, group_id)
);

-- FK: Einladungstoken gehört zu einer Gruppe
ALTER TABLE invitation_tokens
    ADD COLUMN group_id BIGINT REFERENCES gruppen(id) ON DELETE SET NULL;
