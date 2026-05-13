DO $$
DECLARE
    target_schema text := current_schema();
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = target_schema
          AND table_name = 'restaurants'
          AND column_name = 'currency'
          AND data_type = 'character'
          AND character_maximum_length = 3
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I.restaurants ALTER COLUMN currency TYPE varchar(3)',
                target_schema
        );
    END IF;
END $$;
