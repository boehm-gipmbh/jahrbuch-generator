-- Testdaten für @QuarkusTest (DevServices PostgreSQL)
INSERT INTO users (id, name, email, password, created, version)
VALUES (0, 'admin', 'admin@test.de', '$2a$10$7b.9iLgXFVh.r1u9HEbMv.EDL3JcJgldsWHUg4etSUh4wCNGuExye', NOW(), 0)
ON CONFLICT DO NOTHING;
INSERT INTO user_roles (id, role) VALUES (0, 'admin') ON CONFLICT DO NOTHING;
INSERT INTO user_roles (id, role) VALUES (0, 'user')  ON CONFLICT DO NOTHING;

ALTER SEQUENCE IF EXISTS users_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS invitation_tokens_seq RESTART WITH 10;