-- Bestehende Benutzernamen und E-Mail-Adressen zu Kleinbuchstaben normalisieren.
-- Konsistent mit der Registrierungslogik (name/email .toLowerCase()).
UPDATE users SET name = LOWER(name) WHERE name != LOWER(name);
UPDATE users SET email = LOWER(email) WHERE email != LOWER(email);