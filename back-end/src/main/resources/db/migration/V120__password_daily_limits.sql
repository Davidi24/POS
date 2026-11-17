-- Daily password limits (user, 2026-11-17): someone signed in changes their password at most 30 times a day, and
-- "Forgot password?" sends a person at most 10 links a day; past either limit they're told once by email. This keeps
-- what is counted: links sent from "Forgot password?", changes made while signed in, and the limit emails sent.
CREATE TABLE auth_password_events (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        VARCHAR(30) NOT NULL CHECK (kind IN ('RESET_LINK_SENT', 'RESET_LIMIT_EMAIL', 'CHANGED', 'CHANGE_LIMIT_EMAIL')),
    happened_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_password_events_user ON auth_password_events (user_id, kind, happened_at);
