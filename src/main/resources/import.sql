INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (0, 'admin', 'admin@jamsintown.de','$2a$10$7b.9iLgXFVh.r1u9HEbMv.EDL3JcJgldsWHUg4etSUh4wCNGuExye', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'admin')
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'user')
    ON CONFLICT DO NOTHING;
INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (1, 'drdboehm', 'drdboehm@jamsintown.de','$2a$10$P/tywX2InCQVRR7AoN8Wgu9/W0KBnVinyfSMkxfzfoGKUvWiAi1hO', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (1, 'admin')
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (1, 'user')
    ON CONFLICT DO NOTHING;

INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (3, 'abi85', 'abi85@newtown.de','$2a$10$P/tywX2InCQVRR7AoN8Wgu9/W0KBnVinyfSMkxfzfoGKUvWiAi1hO', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (3, 'user')
    ON CONFLICT DO NOTHING;



ALTER SEQUENCE IF EXISTS users_seq RESTART WITH 10;