-- Testdaten: Admin-User für RegistrationFlowTest
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (0, 'admin', 'admin@test.de', '$2a$10$LDvEIg68fkbdmrJjtDRlNOVImzgC3hM28JI3i69jcbE/57L74lijW', NOW(), 0, TRUE, TRUE);
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'admin');
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'user');

-- Testdaten: Gruppe + User für GruppenFlowTest
INSERT INTO "gruppen" ("id", "name", "created_at") VALUES (1, 'TestGruppe', NOW());
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (2, 'gruppenUser', 'gruppen@test.de', '$2a$10$LDvEIg68fkbdmrJjtDRlNOVImzgC3hM28JI3i69jcbE/57L74lijW', NOW(), 0, TRUE, TRUE);
INSERT INTO "user_roles" ("id", "role") VALUES (2, 'user');
INSERT INTO "user_groups" ("user_id", "group_id") VALUES (2, 1);

ALTER SEQUENCE IF EXISTS users_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS gruppen_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS invitation_tokens_seq RESTART WITH 10;
