CREATE TABLE password_reset_token (
  token UUID PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at TIMESTAMP NOT NULL,
  used BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_password_reset_token_user ON password_reset_token(user_id);