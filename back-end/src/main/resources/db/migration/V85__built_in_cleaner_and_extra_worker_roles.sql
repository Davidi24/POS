-- Cleaner and Extra worker are built-in roles now (AppRole, created at startup like the others). Role codes and names
-- are unique among live roles, so a restaurant's own role with the same code or name would stop them being created:
-- such a role keeps its people and permissions under a new name, and can be merged by hand afterwards.
UPDATE roles
SET code = code || '_OWN', name = name || ' (own)', updated_at = now()
WHERE deleted_at IS NULL
  AND restaurant_id IS NOT NULL
  AND (code IN ('CLEANER', 'EXTRA_WORKER') OR lower(name) IN ('cleaner', 'extra worker'));
