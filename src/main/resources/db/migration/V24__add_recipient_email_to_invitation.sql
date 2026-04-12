-- Empfänger-E-Mail für direkte Einladungen per E-Mail.
-- Wenn gesetzt, wurde der Token-Link beim Erstellen an diese Adresse verschickt.
ALTER TABLE invitation_tokens ADD COLUMN recipient_email VARCHAR(255);
