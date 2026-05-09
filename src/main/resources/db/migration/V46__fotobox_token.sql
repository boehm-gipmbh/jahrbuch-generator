CREATE TABLE fotobox_token (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT NOT NULL,
    jti        UUID   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked    BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_fotobox_token_group UNIQUE (group_id)
);
