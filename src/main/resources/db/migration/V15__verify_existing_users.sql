-- Alle Bestandsnutzer als E-Mail-verifiziert markieren.
-- Diese Nutzer haben sich vor Einführung der E-Mail-Verifizierung registriert
-- und sollen sich weiterhin einloggen können.
UPDATE users SET email_verified = TRUE WHERE email_verified = FALSE;
