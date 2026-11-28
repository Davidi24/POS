-- Password links (owner invitations and resets) are kept after a newer one replaces them,
-- so an old link can say why it no longer works.
ALTER TABLE auth_password_reset_tokens ADD COLUMN purpose varchar(20) NOT NULL DEFAULT 'RESET';
ALTER TABLE auth_password_reset_tokens ADD COLUMN superseded_at timestamptz;
ALTER TABLE auth_password_reset_tokens
    ADD CONSTRAINT chk_auth_password_reset_tokens_purpose CHECK (purpose IN ('RESET', 'INVITE'));

-- Links waiting for someone who never set a password are invitations.
UPDATE auth_password_reset_tokens t SET purpose = 'INVITE'
FROM users u
WHERE u.id = t.user_id AND u.email_verified = false;

-- Every account email is saved here in the same transaction as the change that caused it,
-- then sent (and retried) by EmailOutboxDispatcher.
CREATE TABLE email_outbox (
    id uuid PRIMARY KEY,
    kind varchar(40) NOT NULL,
    recipient varchar(320) NOT NULL,
    subject varchar(500) NOT NULL,
    body text,
    status varchar(20) NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    send_before timestamptz,
    last_error varchar(1000),
    sent_at timestamptz,
    user_id uuid,
    restaurant_id uuid,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT chk_email_outbox_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT chk_email_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_email_outbox_due ON email_outbox (next_attempt_at) WHERE status IN ('PENDING', 'SENDING');
CREATE INDEX idx_email_outbox_user_kind ON email_outbox (user_id, kind, created_at DESC);
