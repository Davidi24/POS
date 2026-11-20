-- POS permissions as agreed with the user, one place at a time (2026-11-20).
-- Tables: Change tables (seat, free, out of use, move guests, join) replaces "Seat guests and free tables" and takes joining
-- from "Move orders"; Take payments from a table. Orders: Close as unpaid apart from Void; starting and cancelling an order
-- come with Edit orders. Reservations: Cancel bookings. Menu: Mark sold out, Change prices. My shift: See their pay, See
-- others' shifts. History: Reopen for payment. "See tables" and "See others' shifts" can be given for some floors / roles.

UPDATE permissions SET code = 'TABLES_UPDATE', name = 'TABLES_UPDATE' WHERE code = 'TABLES_SEAT';
UPDATE restaurant_role_permissions SET permission_code = 'TABLES_UPDATE' WHERE permission_code = 'TABLES_SEAT';
UPDATE user_permission_grants SET permission_code = 'TABLES_UPDATE' WHERE permission_code = 'TABLES_SEAT';
UPDATE user_permission_denials SET permission_code = 'TABLES_UPDATE' WHERE permission_code = 'TABLES_SEAT';

INSERT INTO permissions (id, code, name, description, created_at)
SELECT gen_random_uuid(), v.code, v.code, NULL, now()
FROM (VALUES ('TABLES_UPDATE'), ('TABLES_PAYMENT'), ('ORDER_CLOSE_UNPAID'), ('RESERVATION_CANCEL'), ('MENUS_SOLD_OUT'),
             ('MENUS_PRICES'), ('SHIFT_PAY_SELF'), ('SHIFT_TEAM'), ('ORDER_REOPEN_PAYMENT')) AS v(code)
ON CONFLICT (code) DO NOTHING;

-- Who had a permission gets the new ones that now cover part of it. `everywhere` = also for restaurants' changes to
-- built-in roles and for permissions taken away from one person; the rest only adds (own roles, one person's extras).
CREATE TEMPORARY TABLE permission_split (old_code varchar(100), new_code varchar(100), everywhere boolean) ON COMMIT DROP;
INSERT INTO permission_split (old_code, new_code, everywhere) VALUES
    ('ORDER_TRANSFER', 'TABLES_UPDATE', true),
    ('ORDER_CLOSE', 'TABLES_PAYMENT', true),
    ('ORDER_VOID', 'ORDER_CLOSE_UNPAID', true),
    ('RESERVATION_MANAGE', 'RESERVATION_CANCEL', true),
    ('MENUS_UPDATE', 'MENUS_SOLD_OUT', true),
    ('MENUS_UPDATE', 'MENUS_PRICES', true),
    ('SHIFT_SELF', 'SHIFT_PAY_SELF', true),
    ('SHIFT_SELF', 'SHIFT_TEAM', true),
    ('ORDER_CLOSE', 'ORDER_REOPEN_PAYMENT', true),
    ('ORDER_CREATE', 'ORDER_UPDATE', false),
    ('ORDER_CANCEL', 'ORDER_UPDATE', false);

INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), rp.role_id, np.id, now()
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id AND NOT r.is_system
JOIN permissions op ON op.id = rp.permission_id
JOIN permission_split s ON s.old_code = op.code
JOIN permissions np ON np.code = s.new_code
GROUP BY rp.role_id, np.id
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO restaurant_role_permissions (id, restaurant_id, role_id, permission_code, granted, updated_at, updated_by)
SELECT gen_random_uuid(), x.restaurant_id, x.role_id, x.new_code, x.granted, now(), NULL
FROM (
    SELECT DISTINCT ON (rrp.restaurant_id, rrp.role_id, s.new_code) rrp.restaurant_id, rrp.role_id, s.new_code, rrp.granted
    FROM restaurant_role_permissions rrp
    JOIN permission_split s ON s.old_code = rrp.permission_code AND s.everywhere
    ORDER BY rrp.restaurant_id, rrp.role_id, s.new_code, rrp.granted
) x
ON CONFLICT (restaurant_id, role_id, permission_code) DO NOTHING;

INSERT INTO user_permission_grants (id, user_id, permission_code, created_at, created_by)
SELECT gen_random_uuid(), g.user_id, s.new_code, now(), NULL
FROM user_permission_grants g
JOIN permission_split s ON s.old_code = g.permission_code
GROUP BY g.user_id, s.new_code
ON CONFLICT (user_id, permission_code) DO NOTHING;

INSERT INTO user_permission_denials (id, user_id, permission_code, created_at, created_by)
SELECT gen_random_uuid(), d.user_id, s.new_code, now(), NULL
FROM user_permission_denials d
JOIN permission_split s ON s.old_code = d.permission_code AND s.everywhere
GROUP BY d.user_id, s.new_code
ON CONFLICT (user_id, permission_code) DO NOTHING;

-- "Staff see the whole team" off: nobody below the managers sees the team (it's a permission now).
INSERT INTO restaurant_role_permissions (id, restaurant_id, role_id, permission_code, granted, updated_at, updated_by)
SELECT gen_random_uuid(), s.restaurant_id, r.id, 'SHIFT_TEAM', false, now(), NULL
FROM settings s
JOIN roles r ON r.is_system AND r.code IN ('WAITER', 'KITCHEN', 'CLEANER', 'EXTRA_WORKER')
WHERE s.staff_see_whole_team = false
ON CONFLICT (restaurant_id, role_id, permission_code) DO NOTHING;

-- Some floors / roles only. Nothing stored = all of them; one person's rows win over their roles'.
CREATE TABLE permission_scopes (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    role_id uuid REFERENCES roles (id) ON DELETE CASCADE,
    user_id uuid REFERENCES users (id) ON DELETE CASCADE,
    permission_code varchar(100) NOT NULL,
    target varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid REFERENCES users (id),
    CONSTRAINT ck_permission_scopes_owner CHECK ((role_id IS NULL) <> (user_id IS NULL))
);
CREATE UNIQUE INDEX uk_permission_scopes_role ON permission_scopes (restaurant_id, role_id, permission_code, target) WHERE role_id IS NOT NULL;
CREATE UNIQUE INDEX uk_permission_scopes_user ON permission_scopes (restaurant_id, user_id, permission_code, target) WHERE user_id IS NOT NULL;
CREATE INDEX idx_permission_scopes_user ON permission_scopes (user_id);
