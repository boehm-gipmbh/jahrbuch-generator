CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('BILD', 'TEXT', 'VIDEO')),
    target_id BIGINT NOT NULL,
    parent_id BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    user_id BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_comments_target ON comments(target_type, target_id);
CREATE INDEX idx_comments_parent ON comments(parent_id);

CREATE SEQUENCE IF NOT EXISTS comments_seq START WITH 1 INCREMENT BY 50;
