ALTER TABLE restaurants
    ADD COLUMN pending_owner_email varchar(150),
    ADD COLUMN pending_owner_username varchar(50),
    ADD COLUMN pending_owner_first_name varchar(100),
    ADD COLUMN pending_owner_last_name varchar(100),
    ADD COLUMN pending_owner_phone varchar(50),
    ADD COLUMN pending_owner_client_target varchar(20);

DO $$
DECLARE
    constraint_name text;
BEGIN
    FOR constraint_name IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'restaurants'::regclass
          AND contype = 'c'
    LOOP
        EXECUTE format('ALTER TABLE restaurants DROP CONSTRAINT %I', constraint_name);
    END LOOP;
END $$;

ALTER TABLE restaurants
    ADD CONSTRAINT chk_restaurants_valid_state
    CHECK (
        char_length(btrim(name)) > 0
        AND char_length(btrim(legal_name)) > 0
        AND char_length(btrim(code)) > 0
        AND char_length(btrim(slug)) > 0
        AND char_length(currency) = 3
        AND status IN ('PENDING', 'REJECTED', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')
        AND (
            pending_owner_client_target IS NULL
            OR pending_owner_client_target IN ('WEB', 'MOBILE', 'UNIVERSAL')
        )
    );
