-- "Connect devices" (user, 2026-11-25): setting up a till, tablet or screen without the rest of Devices. "Manage devices"
-- now needs it, so whoever could manage devices keeps connecting them. Built-in roles get theirs from startup seeding.
INSERT INTO permissions (id, code, name, description, created_at)
VALUES (gen_random_uuid(), 'DEVICES_CONNECT', 'DEVICES_CONNECT', NULL, now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), rp.role_id, np.id, now()
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id AND NOT r.is_system
JOIN permissions op ON op.id = rp.permission_id AND op.code = 'DEVICES_MANAGE'
JOIN permissions np ON np.code = 'DEVICES_CONNECT'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO restaurant_role_permissions (id, restaurant_id, role_id, permission_code, granted, updated_at, updated_by)
SELECT gen_random_uuid(), rrp.restaurant_id, rrp.role_id, 'DEVICES_CONNECT', true, now(), NULL
FROM restaurant_role_permissions rrp WHERE rrp.permission_code = 'DEVICES_MANAGE' AND rrp.granted
ON CONFLICT (restaurant_id, role_id, permission_code) DO NOTHING;

INSERT INTO user_permission_grants (id, user_id, permission_code, created_at, created_by)
SELECT gen_random_uuid(), g.user_id, 'DEVICES_CONNECT', now(), NULL FROM user_permission_grants g WHERE g.permission_code = 'DEVICES_MANAGE'
ON CONFLICT (user_id, permission_code) DO NOTHING;
