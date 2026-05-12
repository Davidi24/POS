DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'foundation_local'
          AND table_name = 'restaurants'
          AND column_name = 'currency'
          AND data_type = 'character'
          AND character_maximum_length = 3
    ) THEN
        ALTER TABLE foundation_local.restaurants
            ALTER COLUMN currency TYPE varchar(3);
    END IF;
END $$;
