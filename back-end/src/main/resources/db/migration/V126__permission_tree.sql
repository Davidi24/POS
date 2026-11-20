-- Permissions follow a tree (user, 2026-11-19: "someone can't have View off and edit on"; PermissionTree): nothing without
-- the workspace's "Open …" and the part's "View …". The Manager held the Kitchen display's View and Update without being able
-- to open the Kitchen display; role seeding only ever adds, so they're taken away here.
DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id AND rp.permission_id = p.id AND r.is_system
  AND r.code = 'MANAGER' AND p.code IN ('KDS_READ', 'KDS_UPDATE');
