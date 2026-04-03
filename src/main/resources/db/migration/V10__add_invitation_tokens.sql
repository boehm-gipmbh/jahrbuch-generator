CREATE TABLE public.invitation_tokens (
    id           bigint NOT NULL,
    token        uuid NOT NULL,
    label        varchar(255),
    role         varchar(50) NOT NULL DEFAULT 'user',
    active       boolean NOT NULL DEFAULT true,
    expires_at   timestamp with time zone NOT NULL,
    created_at   timestamp with time zone NOT NULL,
    last_used_at timestamp with time zone,
    created_by   bigint NOT NULL,
    CONSTRAINT invitation_tokens_pkey PRIMARY KEY (id),
    CONSTRAINT invitation_tokens_token_key UNIQUE (token),
    CONSTRAINT fk_invitation_tokens_user FOREIGN KEY (created_by) REFERENCES public.users(id)
);

CREATE SEQUENCE IF NOT EXISTS invitation_tokens_seq START WITH 1 INCREMENT BY 50;