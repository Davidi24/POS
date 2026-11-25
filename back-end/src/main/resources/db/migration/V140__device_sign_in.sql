-- Who can sign in on a till or tablet (user, 2026-11-25: "assign the users who can use it, if I want"). No rows = everyone
-- at its branch; otherwise people with one of these roles, or these people. Owners, co-owners, admins and managers always can.
CREATE TABLE device_allowed_roles (
    id         uuid PRIMARY KEY,
    device_id  uuid        NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    role_id    uuid        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_device_allowed_roles UNIQUE (device_id, role_id)
);

CREATE TABLE device_allowed_users (
    id         uuid PRIMARY KEY,
    device_id  uuid        NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_device_allowed_users UNIQUE (device_id, user_id)
);

CREATE INDEX idx_device_allowed_users_user ON device_allowed_users (user_id);
