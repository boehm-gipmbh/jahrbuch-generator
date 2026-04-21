CREATE SEQUENCE videos_seq START WITH 1000 INCREMENT BY 50;

CREATE TABLE videos (
  id             BIGINT PRIMARY KEY DEFAULT nextval('videos_seq'),
  pfad           VARCHAR(255) NOT NULL,
  title          VARCHAR(255) NOT NULL,
  description    TEXT,
  priority       INTEGER,
  story_position INTEGER DEFAULT 0,
  story_column   INTEGER DEFAULT 0,
  user_id        BIGINT NOT NULL REFERENCES users(id),
  group_id       BIGINT REFERENCES gruppen(id),
  story_id       BIGINT REFERENCES stories(id),
  created        TIMESTAMP WITH TIME ZONE NOT NULL,
  version        INTEGER NOT NULL DEFAULT 0,
  deleted        BOOLEAN NOT NULL DEFAULT FALSE
);