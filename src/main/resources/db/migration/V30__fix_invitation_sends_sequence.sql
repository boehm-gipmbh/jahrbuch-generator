-- Hibernate erwartet invitation_sends_seq (nicht invitation_sends_id_seq von BIGSERIAL)
CREATE SEQUENCE IF NOT EXISTS invitation_sends_seq START WITH 1 INCREMENT BY 50;
SELECT setval('invitation_sends_seq', COALESCE((SELECT MAX(id) FROM invitation_sends), 0) + 1);
ALTER TABLE invitation_sends ALTER COLUMN id SET DEFAULT nextval('invitation_sends_seq');
DROP SEQUENCE IF EXISTS invitation_sends_id_seq;