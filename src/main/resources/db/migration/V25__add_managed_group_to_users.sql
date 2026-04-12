ALTER TABLE users ADD COLUMN managed_group_id BIGINT REFERENCES gruppen(id);
