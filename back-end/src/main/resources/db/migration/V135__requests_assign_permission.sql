-- "Assign requests" (REQUESTS_ASSIGN, user 2026-11-21: "I set in a person's permissions who can assign to the other admins"):
-- the Support admin role gets every permission at startup, so it starts switched off for the support admins there are now.
INSERT INTO user_permission_denials (id, user_id, permission_code, created_at)
SELECT gen_random_uuid(), ur.user_id, 'REQUESTS_ASSIGN', now()
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
WHERE r.code = 'CO_SUPER_ADMIN'
ON CONFLICT (user_id, permission_code) DO NOTHING;
