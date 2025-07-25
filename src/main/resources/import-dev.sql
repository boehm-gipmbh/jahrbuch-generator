-- Import initial data for development environment
-- This file is used to populate the database with initial data for development purposes.
-- It should be run after the schema has been created.

-- Create initial user "admin" with email and roles "admin" and "user"
INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (0, 'admin', 'admin@jamsintown.de', '$2a$10$7b.9iLgXFVh.r1u9HEbMv.EDL3JcJgldsWHUg4etSUh4wCNGuExye', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'admin')
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'user')
    ON CONFLICT DO NOTHING;
-- Create initial user "user" with email and role "user"
INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (1, 'user', 'user@jamsintown.de', '$2a$10$7b.9iLgXFVh.r1u9HEbMv.EDL3JcJgldsWHUg4etSUh4wCNGuExye', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (1, 'user')
    ON CONFLICT DO NOTHING;
-- Create initial user "detlef" with email and role "user"
INSERT INTO "users" ("id", "name", "email", "password", "created", "version")
VALUES (2, 'detlef', 'drdboehm@jamsintown.de', '$2a$10$7b.9iLgXFVh.r1u9HEbMv.EDL3JcJgldsWHUg4etSUh4wCNGuExye', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (2, 'user')
    ON CONFLICT DO NOTHING;

INSERT INTO "texte" ("id", "priority","title", "description", "user_id","created", "version")
VALUES (0, 3,'Erste Erinnerung', 'An einem Samstag wie diesem, ja da kann es Mittag werden, und kämmen ist erstmal nicht so wichtig',2,NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "bilder" ("id", "priority", "title","description", "pfad", "user_id", "created", "version")
VALUES (0, 3,'Erstes Bild', 'Beschreibung bitte einfügen', '/home/dboehm/git/git.quarkus/jahrbuch-generator.git/src/main/webui/public/captures/test01.jpg', 2,NOW(), 0)
    ON CONFLICT DO NOTHING;

INSERT into "stories" ("id", "name", "description", "user_id", "created", "version")
VALUES (0, 'Erste Story', 'Schreib was dazu', 2, NOW(), 0)
    ON CONFLICT DO NOTHING;
UPDATE "bilder" SET "story_id" = 0 WHERE "id" = 0;
UPDATE "texte" SET "story_id" = 0 WHERE "id" = 0;


INSERT INTO "texte" ("id", "priority","title", "description", "user_id","created", "version")
VALUES (1, 3,'Einfach nur Kabelsalat', 'Einfach nur mal in die Runde',1,NOW(), 0)
    ON CONFLICT DO NOTHING;

INSERT INTO "bilder" ("id", "priority", "title", "description", "pfad", "user_id", "created", "version")
VALUES (1, 2,'Einfach nur Kabelsalat', '','/home/dboehm/git/git.quarkus/jahrbuch-generator.git/src/main/webui/public/captures/test02.jpg', 1, NOW(), 0)
    ON CONFLICT DO NOTHING;

ALTER SEQUENCE IF EXISTS hibernate_sequence RESTART WITH 10;
ALTER SEQUENCE IF EXISTS bilder_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS users_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS texte_seq RESTART WITH 10;
