DO $$
DECLARE
    schema_name text := current_schema();
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = schema_name
          AND table_name = 'customers'
          AND column_name = 'customer_code'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = schema_name
          AND table_name = 'customers'
          AND column_name = 'code'
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I.customers RENAME COLUMN customer_code TO code',
                schema_name
        );
    END IF;
END $$;

DO $$
DECLARE
    schema_name text := current_schema();
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = schema_name
          AND table_name = 'customers'
          AND constraint_name = 'uk_customers_restaurant_customer_code'
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE table_schema = schema_name
          AND table_name = 'customers'
          AND constraint_name = 'uk_customers_restaurant_code'
    ) THEN
        EXECUTE format(
                'ALTER TABLE %I.customers RENAME CONSTRAINT uk_customers_restaurant_customer_code TO uk_customers_restaurant_code',
                schema_name
        );
    END IF;
END $$;
