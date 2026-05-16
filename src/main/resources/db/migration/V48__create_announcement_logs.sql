CREATE TABLE announcement_logs (
    id                   BIGSERIAL PRIMARY KEY,
    subject              VARCHAR(255) NOT NULL,
    sent_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    recipient_description VARCHAR(255) NOT NULL,
    sent_count           INTEGER      NOT NULL DEFAULT 0,
    failed_count         INTEGER      NOT NULL DEFAULT 0,
    attachment_filename  VARCHAR(255)
);
