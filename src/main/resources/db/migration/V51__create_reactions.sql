CREATE TABLE reactions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id),
    target_type VARCHAR(10)  NOT NULL CHECK (target_type IN ('BILD', 'TEXT')),
    target_id  BIGINT       NOT NULL,
    reaction_type VARCHAR(10) NOT NULL CHECK (reaction_type IN ('LIKE', 'FAVORIT')),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_reaction UNIQUE (user_id, target_type, target_id, reaction_type)
);

CREATE INDEX idx_reactions_target ON reactions (target_type, target_id);
CREATE INDEX idx_reactions_user   ON reactions (user_id);
