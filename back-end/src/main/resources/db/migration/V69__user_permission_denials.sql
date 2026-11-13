-- Permissions switched off for one person (the Super Admin's ticks for each Co-Super-Admin). They're taken away from
-- whatever that person's roles grant, on every request.
CREATE TABLE user_permission_denials (
    id              UUID PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_code VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      UUID         REFERENCES users (id),
    CONSTRAINT uk_user_permission_denials UNIQUE (user_id, permission_code)
);
