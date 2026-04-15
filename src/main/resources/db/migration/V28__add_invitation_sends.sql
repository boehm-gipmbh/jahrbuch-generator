CREATE TABLE invitation_sends (
  id         BIGSERIAL PRIMARY KEY,
  token_id   BIGINT NOT NULL REFERENCES invitation_tokens(id) ON DELETE CASCADE,
  sent_to    VARCHAR(255) NOT NULL,
  sent_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_invitation_sends_token_id ON invitation_sends(token_id);
