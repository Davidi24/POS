DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = target_schema
          AND table_name = 'device-pairing-tokens'
          AND column_name = 'revoked_at'
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I."device-pairing-tokens" ADD COLUMN revoked_at timestamptz',
                target_schema
        );
    END IF;
END $$;

DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = target_schema
          AND t.relname = 'device-pairing-tokens'
          AND c.conname = 'chk_device_pairing_tokens_revocation_window'
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I."device-pairing-tokens" ADD CONSTRAINT chk_device_pairing_tokens_revocation_window CHECK (revoked_at IS NULL OR revoked_at >= created_at)',
                target_schema
        );
    END IF;
END $$;

DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = target_schema
          AND t.relname = 'device-pairing-tokens'
          AND c.conname = 'chk_device_pairing_tokens_terminal_state'
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I."device-pairing-tokens" ADD CONSTRAINT chk_device_pairing_tokens_terminal_state CHECK (used_at IS NULL OR revoked_at IS NULL)',
                target_schema
        );
    END IF;
END $$;

DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    EXECUTE format(
            'CREATE INDEX IF NOT EXISTS idx_device_pairing_tokens_revoked_at ON %I."device-pairing-tokens" (revoked_at)',
            target_schema
    );
END $$;

DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS uk_device_pairing_tokens_open_device ON %I."device-pairing-tokens" (device_id) WHERE used_at IS NULL AND revoked_at IS NULL',
            target_schema
    );
END $$;

DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON c.conrelid = t.oid
        JOIN pg_namespace n ON t.relnamespace = n.oid
        WHERE n.nspname = target_schema
          AND t.relname = 'device-assignments'
          AND c.conname = 'chk_device_assignments_type'
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I."device-assignments" ADD CONSTRAINT chk_device_assignments_type CHECK (assignment_type IN (''BRANCH'', ''USER''))',
                target_schema
        );
    END IF;
END $$;

DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    EXECUTE format(
            'CREATE UNIQUE INDEX IF NOT EXISTS uk_device_assignments_active_device ON %I."device-assignments" (device_id) WHERE is_active = true',
            target_schema
    );
END $$;
