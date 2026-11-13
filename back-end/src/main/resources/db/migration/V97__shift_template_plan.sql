-- When a usual shift runs (user, 2026-10-31: "Plan shifts only applies the shift, then in the day window check who works"):
-- its weekdays ("0,1,2,3,4", 0 = Monday), from which day, and for how long (length_unit DAYS, WEEKS or MONTHS with
-- length_count; no unit = forever). Without weekdays it isn't on any day. So the shift goes on, not a person, and the
-- per-person repeats of V96 go.
ALTER TABLE shift_templates
    ADD COLUMN weekdays     VARCHAR(13),
    ADD COLUMN starts_on    DATE,
    ADD COLUMN length_unit  VARCHAR(10),
    ADD COLUMN length_count INT;

ALTER TABLE shifts DROP COLUMN repeat_id;
DROP TABLE shift_repeats;
