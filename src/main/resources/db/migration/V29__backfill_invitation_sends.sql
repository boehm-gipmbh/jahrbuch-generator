-- Bestehende Versand-Einträge aus invitation_tokens in invitation_sends übertragen
INSERT INTO invitation_sends (token_id, sent_to, sent_at)
SELECT id, recipient_email, sent_at
FROM invitation_tokens
WHERE recipient_email IS NOT NULL
  AND sent_at IS NOT NULL;
