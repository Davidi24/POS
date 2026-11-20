-- A person changing their own username from Profile (user, 2026-11-18): at most 3 changes in 30 days, an old username stays
-- reserved for 30 days so nobody else takes it while colleagues still type it, and Users shows "was …" for 30 days.
CREATE TABLE username_changes (
    id           UUID        PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    old_username VARCHAR(50) NOT NULL,
    new_username VARCHAR(50) NOT NULL,
    changed_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_username_changes_user ON username_changes (user_id, changed_at);
CREATE INDEX idx_username_changes_old ON username_changes (old_username, changed_at);

-- Profile → Security → Recent sign-ins names the device each try came from (the app's X-Device-Name).
ALTER TABLE auth_login_attempts
    ADD COLUMN device_name VARCHAR(100);
