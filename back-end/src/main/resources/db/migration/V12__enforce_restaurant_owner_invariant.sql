ALTER TABLE restaurants
    DROP CONSTRAINT IF EXISTS chk_restaurants_valid_state;

ALTER TABLE restaurants
    ADD CONSTRAINT chk_restaurants_valid_state
    CHECK (
        char_length(btrim(name)) > 0
        AND char_length(btrim(legal_name)) > 0
        AND char_length(btrim(code)) > 0
        AND char_length(btrim(slug)) > 0
        AND char_length(currency) = 3
        AND status IN ('PENDING', 'REJECTED', 'ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')
        AND (owner_id IS NOT NULL OR status IN ('PENDING', 'REJECTED'))
        AND (
            pending_owner_client_target IS NULL
            OR pending_owner_client_target IN ('WEB', 'MOBILE', 'UNIVERSAL')
        )
    ) NOT VALID;
