-- PIN sign-in on the restaurant's connected tills (user, 2026-11-18): a 5-digit PIN, generated (never typed), unique in its
-- restaurant (a peppered hash, so the server finds the person by it); 10 suggestions and 3 changes a day; a manager can
-- reset it. PIN sessions don't count toward the 5 devices.
ALTER TABLE user_sessions DROP CONSTRAINT IF EXISTS user_sessions_session_type_check;
ALTER TABLE user_sessions ADD CONSTRAINT user_sessions_session_type_check CHECK (session_type IN ('PASSWORD', 'PIN'));

CREATE UNIQUE INDEX uk_users_restaurant_pin ON users (restaurant_id, pin_hash) WHERE pin_hash IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE pin_events (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind        VARCHAR(20) NOT NULL CHECK (kind IN ('SUGGESTED', 'SET', 'RESET')),
    pin_hash    TEXT,
    actor_id    UUID,
    happened_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_pin_events_user ON pin_events (user_id, kind, happened_at);

ALTER TABLE auth_login_attempts DROP CONSTRAINT IF EXISTS auth_login_attempts_failure_reason_check;
ALTER TABLE auth_login_attempts ADD CONSTRAINT auth_login_attempts_failure_reason_check CHECK (failure_reason IN (
    'INVALID_CREDENTIALS', 'ACCOUNT_LOCKED', 'ACCOUNT_INACTIVE', 'EMAIL_NOT_VERIFIED', 'ACCOUNT_RATE_LIMITED', 'IP_RATE_LIMITED',
    'DEVICE_RATE_LIMITED', 'INVALID_PIN', 'PIN_PAUSED'));
