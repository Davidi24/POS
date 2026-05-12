DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'foundation_local'
          AND table_name = 'device-pairing-tokens'
          AND column_name = 'revoked_at'
    ) THEN
        ALTER TABLE foundation_local."device-pairing-tokens"
            ADD COLUMN revoked_at timestamptz;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = 'foundation_local'
          AND t.relname = 'device-pairing-tokens'
          AND c.conname = 'chk_device_pairing_tokens_revocation_window'
    ) THEN
        ALTER TABLE foundation_local."device-pairing-tokens"
            ADD CONSTRAINT chk_device_pairing_tokens_revocation_window
            CHECK (revoked_at IS NULL OR revoked_at >= created_at);
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = 'foundation_local'
          AND t.relname = 'device-pairing-tokens'
          AND c.conname = 'chk_device_pairing_tokens_terminal_state'
    ) THEN
        ALTER TABLE foundation_local."device-pairing-tokens"
            ADD CONSTRAINT chk_device_pairing_tokens_terminal_state
            CHECK (used_at IS NULL OR revoked_at IS NULL);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_device_pairing_tokens_revoked_at
    ON foundation_local."device-pairing-tokens" (revoked_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_device_pairing_tokens_open_device
    ON foundation_local."device-pairing-tokens" (device_id)
    WHERE used_at IS NULL AND revoked_at IS NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = 'foundation_local'
          AND t.relname = 'device-assignments'
          AND c.conname = 'chk_device_assignments_type'
    ) THEN
        ALTER TABLE foundation_local."device-assignments"
            ADD CONSTRAINT chk_device_assignments_type
            CHECK (assignment_type IN ('BRANCH', 'USER'));
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_device_assignments_active_device
    ON foundation_local."device-assignments" (device_id)
    WHERE is_active = true;
