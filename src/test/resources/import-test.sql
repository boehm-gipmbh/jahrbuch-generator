-- Testdaten: Admin-User für RegistrationFlowTest
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (0, 'admin', 'admin@test.de', '$2a$10$LDvEIg68fkbdmrJjtDRlNOVImzgC3hM28JI3i69jcbE/57L74lijW', NOW(), 0, TRUE, TRUE);
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'admin');
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'user');
ALTER SEQUENCE IF EXISTS users_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS invitation_tokens_seq RESTART WITH 10;
