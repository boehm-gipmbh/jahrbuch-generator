ALTER TABLE users ADD COLUMN invitation_expires_at TIMESTAMP;
UPDATE users u SET invitation_expires_at = it.expires_at
  FROM invitation_tokens it WHERE u.used_invitation_id = it.id;
