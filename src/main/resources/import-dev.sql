-- Import initial data for development environment
-- This file is used to populate the database with initial data for development purposes.
-- It should be run after the schema has been created.

-- Create initial user "admin" with email and roles "admin" and "user"
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (0, 'admin', 'admin@jamsintown.de', '$2a$10$LDvEIg68fkbdmrJjtDRlNOVImzgC3hM28JI3i69jcbE/57L74lijW', NOW(), 0, TRUE, TRUE)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'admin')
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (0, 'user')
    ON CONFLICT DO NOTHING;
-- Create initial user "user" with email and role "user"
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (1, 'user', 'user@jamsintown.de', '$2a$10$LDvEIg68fkbdmrJjtDRlNOVImzgC3hM28JI3i69jcbE/57L74lijW', NOW(), 0, TRUE, TRUE)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (1, 'user')
    ON CONFLICT DO NOTHING;
-- Create initial user "detlef" with email and role "user"
INSERT INTO "users" ("id", "name", "email", "password", "created", "version", "email_verified", "active")
VALUES (2, 'detlef', 'drdboehm@jamsintown.de', '$2a$10$LDvEIg68fkbdmrJjtDRlNOVImzgC3hM28JI3i69jcbE/57L74lijW', NOW(), 0, TRUE, TRUE)
    ON CONFLICT DO NOTHING;
INSERT INTO "user_roles" ("id", "role") VALUES (2, 'user')
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

ALTER SEQUENCE IF EXISTS hibernate_sequence RESTART WITH 10;
ALTER SEQUENCE IF EXISTS bilder_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS users_seq RESTART WITH 10;
ALTER SEQUENCE IF EXISTS texte_seq RESTART WITH 10;
