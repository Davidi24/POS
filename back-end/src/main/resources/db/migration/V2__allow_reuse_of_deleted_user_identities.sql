ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_email;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_username;
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_normalized_phone;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_active
    ON users (email)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username_active
    ON users (username)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_normalized_phone_active
    ON users (normalized_phone)
    WHERE deleted_at IS NULL;
