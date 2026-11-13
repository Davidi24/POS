-- Public restaurant applications: the applicant's private status code (only its hash is kept), when it was sent and
-- who reviewed it.
ALTER TABLE restaurants ADD COLUMN application_code_hash VARCHAR(64);
ALTER TABLE restaurants ADD COLUMN application_submitted_at TIMESTAMPTZ;
ALTER TABLE restaurants ADD COLUMN reviewed_at TIMESTAMPTZ;
ALTER TABLE restaurants ADD COLUMN reviewed_by UUID REFERENCES users (id);
CREATE UNIQUE INDEX ux_restaurants_application_code ON restaurants (application_code_hash)
    WHERE application_code_hash IS NOT NULL;
CREATE INDEX idx_restaurants_pending_owner_email ON restaurants (pending_owner_email) WHERE status = 'PENDING';
CREATE INDEX idx_restaurants_pending_owner_username ON restaurants (pending_owner_username) WHERE status = 'PENDING';

-- Each change of a restaurant's owner: who had it, who has it now, whether the new owner was invited, and who did it.
CREATE TABLE restaurant_owner_transfers (
    id                 UUID PRIMARY KEY,
    restaurant_id      UUID         NOT NULL REFERENCES restaurants (id),
    previous_owner_id  UUID         REFERENCES users (id),
    new_owner_id       UUID         NOT NULL REFERENCES users (id),
    new_owner_invited  BOOLEAN      NOT NULL,
    previous_owner_deactivated BOOLEAN NOT NULL,
    sessions_revoked   INTEGER      NOT NULL DEFAULT 0,
    reason             VARCHAR(500),
    transferred_by     UUID         NOT NULL REFERENCES users (id),
    transferred_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_restaurant_owner_transfers CHECK (
        sessions_revoked >= 0 AND (previous_owner_id IS NULL OR previous_owner_id <> new_owner_id)
    )
);
CREATE INDEX idx_restaurant_owner_transfers_restaurant ON restaurant_owner_transfers (restaurant_id, transferred_at DESC);

-- Limits for public, unauthenticated requests (restaurant applications and their status checks). Keys are peppered
-- hashes, so neither IP addresses nor emails are stored.
CREATE TABLE public_rate_limits (
    scope             VARCHAR(32) NOT NULL,
    key_hash          CHAR(64)    NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempt_count     INTEGER     NOT NULL CHECK (attempt_count > 0),
    expires_at        TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_public_rate_limits PRIMARY KEY (scope, key_hash)
);
CREATE INDEX idx_public_rate_limits_expires_at ON public_rate_limits (expires_at);
