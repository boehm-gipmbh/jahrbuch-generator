-- Hibernate-Sequenzen für ID-Generierung (falls nicht bereits vorhanden).
-- Auf existierenden DBs (Fly.io, Prod) hat Hibernate diese beim Start angelegt —
-- CREATE SEQUENCE IF NOT EXISTS ist ein No-op in diesem Fall.
CREATE SEQUENCE IF NOT EXISTS bilder_seq            START WITH 5000 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS texte_seq             START WITH 1000 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS stories_seq           START WITH 2000 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS users_seq             START WITH  200 INCREMENT BY 50;
CREATE SEQUENCE IF NOT EXISTS invitation_sends_seq  START WITH    1 INCREMENT BY 50;
