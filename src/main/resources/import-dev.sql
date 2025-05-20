
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

INSERT INTO "texte" ("id", "title", "text", "user_id","created", "version")
VALUES (0, 'Detlef "ungekämmt" am Schreibtisch :-)', 'An einem Samstag wie diesem, ja da kann es Mittag werden, und kämmen ist erstmal nicht so wichtig',2,NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "bilder" ("id", "description", "pfad", "user_id", "text_id","created", "version")
VALUES (0, 'Detlef "ungekämmt" am Schreibtisch :-)', '/home/dboehm/git/git.quarkus/jahrbuch-generator.git/src/main/frontend/captures/test01.jpg', 2, 0,NOW(), 0)
    ON CONFLICT DO NOTHING;

INSERT INTO "texte" ("id", "title", "text", "user_id","created", "version")
VALUES (1, 'Einfach nur Kabelsalat', 'Einfach nur mal in die Runde',1,NOW(), 0)
    ON CONFLICT DO NOTHING;

INSERT INTO "bilder" ("id", "description", "pfad", "user_id", "text_id","created", "version")
VALUES (1, 'Einfach nur Kabelsalat', '/home/dboehm/git/git.quarkus/jahrbuch-generator.git/src/main/frontend/captures/test02.jpg', 1, 1,NOW(), 0)
    ON CONFLICT DO NOTHING;

ALTER SEQUENCE IF EXISTS hibernate_sequence RESTART WITH 10;
ALTER SEQUENCE IF EXISTS bilder_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS users_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS texte_seq RESTART WITH 10;
