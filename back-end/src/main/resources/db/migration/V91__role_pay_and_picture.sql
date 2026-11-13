-- Who gets paid (user, 2026-10-31: "in Salary only roles that get paid; Viewers and Admins aren't paid and can't be put
-- on shifts"): inside one restaurant a role is paid or not, and paid roles name the Salary team their people are paid in
-- (a "Driver" the restaurant makes goes to Extra workers). Built-in roles are shared by every restaurant, so a restaurant's
-- choice for any role is a row here; without one the role's default applies (RolePayService.defaults).
CREATE TABLE role_pay_settings (
    id            UUID PRIMARY KEY,
    restaurant_id UUID        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    role_id       UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    paid          BOOLEAN     NOT NULL,
    pay_team      VARCHAR(20) NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by    UUID        REFERENCES users (id),
    CONSTRAINT uk_role_pay_settings UNIQUE (restaurant_id, role_id),
    CONSTRAINT ck_role_pay_settings_team CHECK (pay_team IN ('MANAGERS', 'KITCHEN', 'WAITERS', 'CLEANING', 'EXTRA'))
);

-- The picture a restaurant picked for its own role (one of the app's role pictures, by name); built-in roles keep theirs.
ALTER TABLE roles ADD COLUMN picture VARCHAR(40);
