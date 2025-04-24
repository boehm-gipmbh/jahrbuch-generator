-- This file allow to write SQL commands that will be emitted in test and dev.
-- The commands are commented as their support depends of the database
-- insert into myentity (id, field) values(1, 'field-1');
-- insert into myentity (id, field) values(2, 'field-2');
-- insert into myentity (id, field) values(3, 'field-3');
-- alter sequence myentity_seq restart with 4;

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

INSERT INTO "projects" ("id", "name", "created", "version")
    VALUES (0, 'Work', NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "projects_users" ("users_id", "project_id")
    VALUES (0, 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "projects_users" ("users_id", "project_id")
    VALUES (1, 0)
    ON CONFLICT DO NOTHING;


ALTER SEQUENCE IF EXISTS hibernate_sequence RESTART WITH 10;
