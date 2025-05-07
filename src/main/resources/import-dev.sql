
INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (0, 'admin', 'admin@jamsintown.de', '$2a$10$7b.9iLgXFVh.r1u9HEbMv.EDL3JcJgldsWHUg4etSUh4wCNGuExye', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'admin')
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'user')
    ON CONFLICT DO NOTHING;
INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (1, 'user', 'user@jamsintown.de', '$2a$10$7b.9iLgXFVh.r1u9HEbMv.EDL3JcJgldsWHUg4etSUh4wCNGuExye', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (1, 'user')
    ON CONFLICT DO NOTHING;
INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (2, 'detlef', 'drdboehm@jamsintown.de', '$2a$10$7b.9iLgXFVh.r1u9HEbMv.EDL3JcJgldsWHUg4etSUh4wCNGuExye', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (2, 'user')
    ON CONFLICT DO NOTHING;

INSERT INTO "texte" ("id", "title", "text", "created", "version")
VALUES (10, 'Test-Title', 'Test-Text',NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "bilder" ("id", "description", "pfad", "user_id", "text_id","created", "version")
VALUES (0, 'test', 'Test-Pfad', 2, 10,NOW(), 0)
    ON CONFLICT DO NOTHING;

INSERT INTO "texte" ("id", "title", "text", "created", "version")
VALUES (11, 'User-Title', 'User-Text',NOW(), 0)
    ON CONFLICT DO NOTHING;

INSERT INTO "bilder" ("id", "description", "pfad", "user_id", "text_id","created", "version")
VALUES (1, 'Usertest', 'User-Pfad', 1, 11,NOW(), 0)
    ON CONFLICT DO NOTHING;

ALTER SEQUENCE IF EXISTS hibernate_sequence RESTART WITH 20;
