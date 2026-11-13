CREATE TABLE auth_refresh_rate_limit (
    scope VARCHAR(16) NOT NULL,
    key_hash CHAR(64) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempt_count INTEGER NOT NULL CHECK (attempt_count > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_auth_refresh_rate_limit PRIMARY KEY (scope, key_hash),
    CONSTRAINT chk_auth_refresh_rate_limit_scope CHECK (scope IN ('IP', 'TOKEN'))
);

CREATE INDEX idx_auth_refresh_rate_limit_expires_at
    ON auth_refresh_rate_limit (expires_at);
