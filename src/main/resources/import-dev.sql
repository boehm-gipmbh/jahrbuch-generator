-- Import initial data for development environment
-- This file is used to populate the database with initial data for development purposes.
-- It should be run after the schema has been created.

-- Create initial user "admin" with email and roles "admin" and "user"
-- Password: Admin1234!
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (0, 'admin', 'admin@jamsintown.de', '$2a$10$geBz3tkQfSME.ZCPpbPZ4.45.JSNOdcpwbah6lsaZUz4oGf3Ca19K', NOW(), 0, TRUE, TRUE)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'admin')
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'user')
    ON CONFLICT DO NOTHING;
-- Create initial user "user" with email and role "user"
-- Password: User9999#
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (1, 'user', 'user@jamsintown.de', '$2a$10$oELFjREnxztejNQ.ZaNW1uzaGRY5UL8SN4SidPdeg5/igxseREcH.', NOW(), 0, TRUE, TRUE)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (1, 'user')
    ON CONFLICT DO NOTHING;
-- Create initial user "detlef" with email and role "user"
-- Password: Detlef9999#
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (2, 'detlef', 'drdboehm@jamsintown.de', '$2a$10$qzTLYniVo5YgFE0NomjP9.rOdEKEv2ITP/4m6eZpFpIXib.agqTGe', NOW(), 0, TRUE, TRUE)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (2, 'user')
    ON CONFLICT DO NOTHING;

-- Create test groups
INSERT INTO "gruppen" ("id", "name") VALUES (1, 'Hochzeitszeitung')
    ON CONFLICT DO NOTHING;
INSERT INTO "gruppen" ("id", "name") VALUES (2, 'ABI 1985')
    ON CONFLICT DO NOTHING;
INSERT INTO "gruppen" ("id", "name") VALUES (3, 'ABI 1986')
    ON CONFLICT DO NOTHING;

-- Create group-admin user "ddet" with managedGroup=Hochzeitszeitung
-- Password: Ddet9999#
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active", "managed_group_id")
VALUES (3, 'ddet', 'dboehm@arcor.de', '$2a$10$IPSNwwU5ehCTd4XBXARvkOqCXayHNfs5TFLUMs5xghl0GcuP5z2ou', NOW(), 0, TRUE, TRUE, 1)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (3, 'group-admin')
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (3, 'user')
    ON CONFLICT DO NOTHING;

-- Add ddet to all groups
INSERT INTO "user_groups" ("user_id", "group_id") VALUES (3, 1)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_groups" ("user_id", "group_id") VALUES (3, 2)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_groups" ("user_id", "group_id") VALUES (3, 3)
    ON CONFLICT DO NOTHING;

INSERT INTO "texte" ("id", "priority","title", "description", "user_id","created", "version")
VALUES (0, 3,'Erste Erinnerung', 'Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet. Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut labore et dolore magna aliquyam erat, sed diam voluptua. At vero eos et accusam et justo duo dolores et ea rebum. Stet clita kasd gubergren, no sea takimata sanctus est Lorem ipsum dolor sit amet.',2,NOW(), 0)
    ON CONFLICT DO NOTHING;
INSERT INTO "bilder" ("id", "priority", "title","description", "pfad", "user_id", "created", "version")
VALUES (0, 3,'Erstes Bild', 'Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat.', '/test01.jpg', 2,NOW(), 0)
    ON CONFLICT DO NOTHING;

INSERT into "stories" ("id", "name", "description", "user_id", "created", "version")
VALUES (0, 'Erste Story', 'Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat. Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis at vero eros et accumsan et iusto odio dignissim qui blandit praesent luptatum zzril delenit augue duis dolore te feugait nulla facilisi.', 2, NOW(), 0)
    ON CONFLICT DO NOTHING;
UPDATE "bilder" SET "story_id" = 0 WHERE "id" = 0;
UPDATE "texte" SET "story_id" = 0 WHERE "id" = 0;


INSERT INTO "texte" ("id", "priority","title", "description", "user_id","created", "version")
VALUES (1, 3,'Anderer Text', 'Nam liber tempor cum soluta nobis eleifend option congue nihil imperdiet doming id quod mazim placerat facer possim assum. Lorem ipsum dolor sit amet, consectetuer adipiscing elit, sed diam nonummy nibh euismod tincidunt ut laoreet dolore magna aliquam erat volutpat. Ut wisi enim ad minim veniam, quis nostrud exerci tation ullamcorper suscipit lobortis nisl ut aliquip ex ea commodo consequat.
',1,NOW(), 0)
    ON CONFLICT DO NOTHING;

INSERT INTO "bilder" ("id", "priority", "title", "description", "pfad", "user_id", "created", "version")
VALUES (1, 2,'Anderes Bild', 'Duis autem vel eum iriure dolor in hendrerit in vulputate velit esse molestie consequat, vel illum dolore eu feugiat nulla facilisis. ','/test02.jpg', 1, NOW(), 0)
    ON CONFLICT DO NOTHING;

ALTER SEQUENCE IF EXISTS bilder_seq RESTART WITH 100;
ALTER SEQUENCE IF EXISTS users_seq RESTART WITH 100;
ALTER SEQUENCE IF EXISTS texte_seq RESTART WITH 100;
ALTER SEQUENCE IF EXISTS gruppen_seq RESTART WITH 100;
