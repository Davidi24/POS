-- Permissions by place (user, 2026-11-19: "a card for each, Tables, Orders, Menu…; the kitchen sees only the tickets or
-- Upcoming; Inventory's stock and items each with their own; Statistics' Sales without Finance; all nav bars with chosen").
-- Each place of each workspace's bar has its own "See …" and what changes things there has its own permission.

-- The new permissions (startup seeding gives them their names and adds them to the built-in roles).
INSERT INTO permissions (id, code, name, description, created_at)
SELECT gen_random_uuid(), v.code, v.code, NULL, now()
FROM (VALUES
    ('TABLES_READ'),
    ('TABLES_SEAT'),
    ('TABLES_LAYOUT'),
    ('KITCHEN_STATUS_READ'),
    ('SALES_SELF'),
    ('ORDER_HISTORY_READ'),
    ('KDS_UPCOMING_READ'),
    ('KDS_MENU_READ'),
    ('KDS_HISTORY_READ'),
    ('INVENTORY_STOCK_READ'),
    ('INVENTORY_RECEIVE'),
    ('INVENTORY_WASTE'),
    ('INVENTORY_TRANSFER'),
    ('INVENTORY_ADJUST'),
    ('INVENTORY_PLACES'),
    ('INVENTORY_ITEMS_READ'),
    ('INVENTORY_ITEMS_CREATE'),
    ('INVENTORY_ITEMS_UPDATE'),
    ('INVENTORY_ITEMS_DELETE'),
    ('INVENTORY_COUNTS_READ'),
    ('INVENTORY_COUNT'),
    ('INVENTORY_COUNTS_APPROVE'),
    ('INVENTORY_HISTORY_READ'),
    ('STATS_SALES_READ'),
    ('STATS_FINANCE_READ'),
    ('STATS_ORDERS_READ'),
    ('STATS_DISHES_READ'),
    ('STATS_STOCK_READ'),
    ('STATS_TABLES_READ'),
    ('STATS_TEAM_READ')
) AS v(code)
ON CONFLICT (code) DO NOTHING;

-- Nobody loses anything: whoever had a permission gets the new ones that now cover part of what it allowed.
CREATE TEMPORARY TABLE permission_split (old_code varchar(100), new_code varchar(100)) ON COMMIT DROP;
INSERT INTO permission_split (old_code, new_code) VALUES
    ('ORDER_READ', 'TABLES_READ'),
    ('ORDER_READ', 'KITCHEN_STATUS_READ'),
    ('ORDER_READ', 'ORDER_HISTORY_READ'),
    ('ORDER_CREATE', 'TABLES_SEAT'),
    ('ORDER_CREATE', 'SALES_SELF'),
    ('ORDER_UPDATE', 'TABLES_SEAT'),
    ('SETTINGS_UPDATE', 'TABLES_LAYOUT'),
    ('KDS_READ', 'KDS_UPCOMING_READ'),
    ('KDS_READ', 'KDS_MENU_READ'),
    ('KDS_READ', 'KDS_HISTORY_READ'),
    ('INVENTORY_READ', 'INVENTORY_STOCK_READ'),
    ('INVENTORY_READ', 'INVENTORY_ITEMS_READ'),
    ('INVENTORY_READ', 'INVENTORY_COUNTS_READ'),
    ('INVENTORY_READ', 'INVENTORY_HISTORY_READ'),
    ('INVENTORY_UPDATE', 'INVENTORY_RECEIVE'),
    ('INVENTORY_UPDATE', 'INVENTORY_WASTE'),
    ('INVENTORY_UPDATE', 'INVENTORY_TRANSFER'),
    ('INVENTORY_UPDATE', 'INVENTORY_ADJUST'),
    ('INVENTORY_UPDATE', 'INVENTORY_PLACES'),
    ('INVENTORY_UPDATE', 'INVENTORY_ITEMS_CREATE'),
    ('INVENTORY_UPDATE', 'INVENTORY_ITEMS_UPDATE'),
    ('INVENTORY_UPDATE', 'INVENTORY_ITEMS_DELETE'),
    ('INVENTORY_UPDATE', 'INVENTORY_COUNT'),
    ('INVENTORY_UPDATE', 'INVENTORY_COUNTS_APPROVE'),
    ('REPORTS_READ', 'STATS_SALES_READ'),
    ('REPORTS_READ', 'STATS_FINANCE_READ'),
    ('REPORTS_READ', 'STATS_ORDERS_READ'),
    ('REPORTS_READ', 'STATS_DISHES_READ'),
    ('REPORTS_READ', 'STATS_STOCK_READ'),
    ('REPORTS_READ', 'STATS_TABLES_READ'),
    ('REPORTS_READ', 'STATS_TEAM_READ');

-- The restaurants' own roles (built-in roles get theirs from AppRole at startup).
INSERT INTO role_permissions (id, role_id, permission_id, created_at)
SELECT gen_random_uuid(), rp.role_id, np.id, now()
FROM role_permissions rp
JOIN roles r ON r.id = rp.role_id AND NOT r.is_system
JOIN permissions op ON op.id = rp.permission_id
JOIN permission_split s ON s.old_code = op.code
JOIN permissions np ON np.code = s.new_code
GROUP BY rp.role_id, np.id
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- A restaurant's changes to a built-in role: what was added or taken away goes for the new ones too.
INSERT INTO restaurant_role_permissions (id, restaurant_id, role_id, permission_code, granted, updated_at, updated_by)
SELECT gen_random_uuid(), x.restaurant_id, x.role_id, x.new_code, x.granted, now(), NULL
FROM (
    SELECT DISTINCT ON (rrp.restaurant_id, rrp.role_id, s.new_code) rrp.restaurant_id, rrp.role_id, s.new_code, rrp.granted
    FROM restaurant_role_permissions rrp
    JOIN permission_split s ON s.old_code = rrp.permission_code
    ORDER BY rrp.restaurant_id, rrp.role_id, s.new_code, rrp.granted
) x
ON CONFLICT (restaurant_id, role_id, permission_code) DO NOTHING;

-- One person's own extra permissions and the ones taken away from them.
INSERT INTO user_permission_grants (id, user_id, permission_code, created_at, created_by)
SELECT gen_random_uuid(), g.user_id, s.new_code, now(), NULL
FROM user_permission_grants g
JOIN permission_split s ON s.old_code = g.permission_code
GROUP BY g.user_id, s.new_code
ON CONFLICT (user_id, permission_code) DO NOTHING;

INSERT INTO user_permission_denials (id, user_id, permission_code, created_at, created_by)
SELECT gen_random_uuid(), d.user_id, s.new_code, now(), NULL
FROM user_permission_denials d
JOIN permission_split s ON s.old_code = d.permission_code
GROUP BY d.user_id, s.new_code
ON CONFLICT (user_id, permission_code) DO NOTHING;

-- "Change Stock" is now Receive, Waste, Move, Correct, Manage places, Add/Change/Delete items, Count and Approve counts.
DELETE FROM role_permissions WHERE permission_id IN (SELECT id FROM permissions WHERE code = 'INVENTORY_UPDATE');
DELETE FROM restaurant_role_permissions WHERE permission_code = 'INVENTORY_UPDATE';
DELETE FROM user_permission_grants WHERE permission_code = 'INVENTORY_UPDATE';
DELETE FROM user_permission_denials WHERE permission_code = 'INVENTORY_UPDATE';
DELETE FROM permissions WHERE code = 'INVENTORY_UPDATE';
