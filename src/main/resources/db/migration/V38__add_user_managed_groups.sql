CREATE TABLE user_managed_groups (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  group_id BIGINT NOT NULL REFERENCES gruppen(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, group_id)
);

INSERT INTO user_managed_groups (user_id, group_id)
SELECT id, managed_group_id FROM users WHERE managed_group_id IS NOT NULL;
