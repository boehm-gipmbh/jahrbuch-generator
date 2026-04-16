ALTER TABLE invitation_sends ADD COLUMN IF NOT EXISTS resend_message_id VARCHAR(255);
