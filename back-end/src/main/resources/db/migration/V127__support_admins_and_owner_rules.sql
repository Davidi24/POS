-- Users & roles, step 3 (user, 2026-11-19).
-- Support admins: the Super Admin's helpers (role CO_SUPER_ADMIN, shown as "Admin") take care of the restaurants the Super
-- Admin gives them, or all of them; they remove owners and co-owners only when the Super Admin allows it.
ALTER TABLE users ADD COLUMN admin_all_restaurants boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN admin_may_remove_owners boolean NOT NULL DEFAULT false;

CREATE TABLE admin_restaurant_access (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    restaurant_id uuid NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    PRIMARY KEY (user_id, restaurant_id)
);
CREATE INDEX idx_admin_restaurant_access_restaurant ON admin_restaurant_access (restaurant_id);

-- What a restaurant asks the Super Admin: to remove one of its owners (owners never remove each other), or for a support admin.
CREATE TABLE platform_requests (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES restaurants (id),
    kind varchar(30) NOT NULL CHECK (kind IN ('REMOVE_OWNER', 'NEED_ADMIN')),
    target_user_id uuid REFERENCES users (id),
    note varchar(2000),
    status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED')),
    created_by uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL,
    decided_by uuid REFERENCES users (id),
    decided_at timestamptz,
    decision_note varchar(2000)
);
CREATE INDEX idx_platform_requests_status ON platform_requests (status, created_at);

-- Existing Co-Super-Admins keep reaching every restaurant.
UPDATE users u SET admin_all_restaurants = true
FROM user_roles ur JOIN roles r ON r.id = ur.role_id
WHERE ur.user_id = u.id AND r.code = 'CO_SUPER_ADMIN';

-- Admin: the Admin Hub's shifts, users, settings (without money and fraud) and audit log, Inventory and Statistics. Role
-- seeding only ever adds, so the rest is taken away here.
DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id AND rp.permission_id = p.id AND r.is_system AND r.code = 'ADMIN'
  AND p.code NOT IN ('RESTAURANTS_READ', 'USERS_CREATE', 'USERS_READ', 'USERS_UPDATE', 'USERS_DELETE', 'ROLES_READ',
                     'SETTINGS_READ', 'SETTINGS_UPDATE', 'SETTINGS_AUDIT', 'SHIFT_READ', 'SHIFT_MANAGE', 'REPORTS_READ',
                     'ADMIN_ACCESS', 'STATISTICS_ACCESS', 'INVENTORY_ACCESS', 'INVENTORY_READ', 'INVENTORY_UPDATE',
                     'SUPPLIERS_READ', 'SUPPLIERS_UPDATE');

-- Existing admins keep POS and the kitchen display: they get the roles under Admin, as new admins do by default.
INSERT INTO user_roles (id, user_id, role_id, assigned_at, assigned_by)
SELECT gen_random_uuid(), ur.user_id, below.id, now(), NULL
FROM user_roles ur
JOIN roles a ON a.id = ur.role_id AND a.code = 'ADMIN'
JOIN roles below ON below.is_system AND below.code IN ('MANAGER', 'WAITER', 'VIEWER', 'KITCHEN', 'CLEANER', 'EXTRA_WORKER')
WHERE NOT EXISTS (SELECT 1 FROM user_roles x WHERE x.user_id = ur.user_id AND x.role_id = below.id);
