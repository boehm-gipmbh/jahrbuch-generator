-- Gruppen-Tabelle (Label des Einladungslinks wird zur Gruppe)
CREATE TABLE gruppen (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE SEQUENCE gruppen_seq START WITH 1 INCREMENT BY 50;
