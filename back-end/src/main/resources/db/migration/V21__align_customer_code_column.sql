DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'foundation_local'
          AND table_name = 'customers'
          AND column_name = 'customer_code'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'foundation_local'
          AND table_name = 'customers'
          AND column_name = 'code'
    ) THEN
        ALTER TABLE foundation_local.customers
            RENAME COLUMN customer_code TO code;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = 'foundation_local'
          AND table_name = 'customers'
          AND constraint_name = 'uk_customers_restaurant_customer_code'
    ) THEN
        ALTER TABLE foundation_local.customers
            RENAME CONSTRAINT uk_customers_restaurant_customer_code TO uk_customers_restaurant_code;
    END IF;
END $$;
