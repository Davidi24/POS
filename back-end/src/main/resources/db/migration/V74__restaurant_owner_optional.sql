-- A restaurant is created first and its owner is added in a second step, so it can exist without an owner for a while.
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
        AND (
            pending_owner_client_target IS NULL
            OR pending_owner_client_target IN ('WEB', 'MOBILE', 'UNIVERSAL')
        )
    ) NOT VALID;
