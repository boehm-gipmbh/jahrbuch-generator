-- Setze managedGroup für bestehende group-admins
-- Jeder group-admin bekommt seine erste Gruppe (nach ID) als managedGroup zugewiesen
UPDATE users u
SET managed_group_id = (
  SELECT g.group_id FROM user_groups g
  WHERE g.user_id = u.id
  ORDER BY g.group_id ASC
  LIMIT 1
)
WHERE u.id IN (SELECT id FROM user_roles WHERE role = 'group-admin')
  AND u.managed_group_id IS NULL;
