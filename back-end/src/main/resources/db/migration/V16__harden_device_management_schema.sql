ALTER TABLE "device-pairing-tokens"
    ADD COLUMN revoked_at timestamptz;

ALTER TABLE "device-pairing-tokens"
    ADD CONSTRAINT chk_device_pairing_tokens_revocation_window
    CHECK (revoked_at IS NULL OR revoked_at >= created_at);

ALTER TABLE "device-pairing-tokens"
    ADD CONSTRAINT chk_device_pairing_tokens_terminal_state
    CHECK (used_at IS NULL OR revoked_at IS NULL);

CREATE INDEX idx_device_pairing_tokens_revoked_at
    ON "device-pairing-tokens" (revoked_at);

CREATE UNIQUE INDEX uk_device_pairing_tokens_open_device
    ON "device-pairing-tokens" (device_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

ALTER TABLE "device-assignments"
    ADD CONSTRAINT chk_device_assignments_type
    CHECK (assignment_type IN ('BRANCH', 'USER'));

CREATE UNIQUE INDEX uk_device_assignments_active_device
    ON "device-assignments" (device_id)
    WHERE is_active = true;
