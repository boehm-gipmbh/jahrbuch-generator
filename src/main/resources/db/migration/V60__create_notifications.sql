CREATE SEQUENCE IF NOT EXISTS notifications_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY DEFAULT nextval('notifications_seq'),
    recipient_id BIGINT NOT NULL REFERENCES users(id),
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('BILD', 'TEXT', 'VIDEO', 'COMMENT')),
    target_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    reporter_name VARCHAR(255),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, read_at);
