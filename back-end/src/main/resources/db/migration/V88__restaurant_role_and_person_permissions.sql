-- Inside one restaurant a built-in role's permissions can differ from the app's defaults (user, 2026-10-30: "change all
-- the permissions of a role"): granted = true adds a permission to the role there, false takes it away. Built-in roles
-- are shared by every restaurant, so their own permissions stay as they are.
CREATE TABLE restaurant_role_permissions (
    id              UUID PRIMARY KEY,
    restaurant_id   UUID         NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    role_id         UUID         NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL,
    granted         BOOLEAN      NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      UUID         REFERENCES users (id),
    CONSTRAINT uk_restaurant_role_permissions UNIQUE (restaurant_id, role_id, permission_code)
);

CREATE INDEX idx_restaurant_role_permissions_restaurant ON restaurant_role_permissions (restaurant_id, role_id);

-- One person's own extra permissions on top of what their roles give ("every person has the button permission";
-- user_permission_denials takes some away).
CREATE TABLE user_permission_grants (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID         REFERENCES users (id),
    CONSTRAINT uk_user_permission_grants UNIQUE (user_id, permission_code)
);
