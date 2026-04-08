-- User löschen soll alle zugehörigen Inhalte mitlöschen
ALTER TABLE bilder  DROP CONSTRAINT fkcfqc4r13lthh8qohgggfafqkv;
ALTER TABLE bilder  ADD CONSTRAINT bilder_user_id_fk  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE texte   DROP CONSTRAINT fkr6in5lpmvn57qp99qfevec75c;
ALTER TABLE texte   ADD CONSTRAINT texte_user_id_fk   FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE stories DROP CONSTRAINT fkshv2ytgbsn9w9mpu43mc6ln6j;
ALTER TABLE stories ADD CONSTRAINT stories_user_id_fk FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
