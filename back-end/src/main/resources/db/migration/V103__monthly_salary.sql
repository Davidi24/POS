-- Someone is paid by the hour or by the month: one of the two.
ALTER TABLE staff_pay_rates ALTER COLUMN hourly_rate DROP NOT NULL;
ALTER TABLE staff_pay_rates ADD COLUMN monthly_salary NUMERIC(12,2);
ALTER TABLE staff_pay_rates ADD CONSTRAINT ck_staff_pay_rates_one CHECK ((hourly_rate IS NULL) <> (monthly_salary IS NULL));
ALTER TABLE staff_pay_rates ADD CONSTRAINT ck_staff_pay_rates_salary CHECK (monthly_salary IS NULL OR (monthly_salary >= 0 AND monthly_salary <= 10000000));
