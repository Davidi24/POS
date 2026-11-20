-- An order's own kitchen status is its own permission in Orders; "See Kitchen Status" is the whole board (user,
-- 2026-11-20). Whoever saw orders keeps seeing their kitchen status.
INSERT INTO permissions (id, code, name, description, created_at)
VALUES (gen_random_uuid(), 'ORDER_KITCHEN_READ', 'ORDER_KITCHEN_READ', NULL, now())
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), rp.role_id, np.id, now()
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id AND NOT r.is_system
JOIN permissions op ON op.id = rp.permission_id AND op.code = 'ORDER_READ'
JOIN permissions np ON np.code = 'ORDER_KITCHEN_READ'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO restaurant_role_permissions (id, restaurant_id, role_id, permission_code, granted, updated_at, updated_by)
SELECT gen_random_uuid(), rrp.restaurant_id, rrp.role_id, 'ORDER_KITCHEN_READ', rrp.granted, now(), NULL
FROM restaurant_role_permissions rrp WHERE rrp.permission_code = 'ORDER_READ'
ON CONFLICT (restaurant_id, role_id, permission_code) DO NOTHING;

INSERT INTO user_permission_grants (id, user_id, permission_code, created_at, created_by)
SELECT gen_random_uuid(), g.user_id, 'ORDER_KITCHEN_READ', now(), NULL FROM user_permission_grants g WHERE g.permission_code = 'ORDER_READ'
ON CONFLICT (user_id, permission_code) DO NOTHING;

INSERT INTO user_permission_denials (id, user_id, permission_code, created_at, created_by)
SELECT gen_random_uuid(), d.user_id, 'ORDER_KITCHEN_READ', now(), NULL FROM user_permission_denials d WHERE d.permission_code = 'ORDER_READ'
ON CONFLICT (user_id, permission_code) DO NOTHING;
