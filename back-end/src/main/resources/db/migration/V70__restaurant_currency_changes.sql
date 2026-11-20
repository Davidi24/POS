-- Each change of a restaurant's currency: the old and new currency, the rate used to convert its prices
-- (1 old = rate new), the rounding and who did it. Past sales keep the currency they were made in; reports use these
-- records to tell the periods apart.
CREATE TABLE restaurant_currency_changes (
    id               UUID PRIMARY KEY,
    restaurant_id    UUID           NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    old_currency     VARCHAR(3)     NOT NULL,
    new_currency     VARCHAR(3)     NOT NULL,
    rate             NUMERIC(20, 8) NOT NULL,
    rounding_step    NUMERIC(12, 2) NOT NULL,
    values_converted INTEGER        NOT NULL DEFAULT 0,
    changed_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    changed_by       UUID           REFERENCES users (id),
    CONSTRAINT ck_restaurant_currency_changes CHECK (rate > 0 AND rounding_step > 0 AND old_currency <> new_currency)
);
CREATE INDEX idx_restaurant_currency_changes_restaurant ON restaurant_currency_changes (restaurant_id, changed_at);

-- Amounts in currencies such as ALL or JPY are much larger numbers than in EUR: room for them after a conversion.
ALTER TABLE settings ALTER COLUMN card_fee_fixed TYPE NUMERIC(12, 2);
ALTER TABLE settings DROP CONSTRAINT ck_settings_card_fee;
ALTER TABLE settings ADD CONSTRAINT ck_settings_card_fee
    CHECK (card_fee_percent BETWEEN 0 AND 20 AND card_fee_fixed BETWEEN 0 AND 1000000);
ALTER TABLE staff_pay_rates DROP CONSTRAINT ck_staff_pay_rates_rate;
ALTER TABLE staff_pay_rates ADD CONSTRAINT ck_staff_pay_rates_rate CHECK (hourly_rate >= 0 AND hourly_rate <= 10000000);
