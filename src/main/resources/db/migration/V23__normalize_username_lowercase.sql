-- Bestehende Benutzernamen zu Kleinbuchstaben normalisieren.
-- Konsistent mit der Registrierungslogik (name.toLowerCase()).
UPDATE users SET name = LOWER(name) WHERE name != LOWER(name);