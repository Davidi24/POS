-- Replaces the single reorder_quantity field on inventory_levels with a proper two-tier
-- reorder settings model: a system-calculated value (populated by a later phase) and an
-- explicit manager override. Any existing non-null reorder_quantity value was always
-- entered directly by a person, never computed by a formula, so it's preserved as a
-- manual override rather than lost -- calculated_reorder_point stays null for those rows
-- until the calculation logic exists.

alter table inventory_levels
    add column calculated_reorder_point numeric(12, 3),
    add column manual_reorder_point numeric(12, 3);

update inventory_levels
set manual_reorder_point = reorder_quantity
where reorder_quantity is not null;

-- The original check constraint on this table was created inline (unnamed), so Postgres
-- auto-generated its name. Look it up dynamically instead of guessing it, so this migration
-- doesn't depend on Postgres's default naming behavior being exactly what's expected.
do $$
    DECLARE
        target_schema text := current_schema();
        old_check_name text;
    BEGIN
        SELECT con.conname
        INTO old_check_name
        FROM pg_constraint con
                 JOIN pg_class rel ON rel.oid = con.conrelid
                 JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
        WHERE rel.relname = 'inventory_levels'
          AND nsp.nspname = target_schema
          AND con.contype = 'c'
          AND pg_get_constraintdef(con.oid) LIKE '%reorder_quantity%';

        IF old_check_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I.inventory_levels DROP CONSTRAINT %I', target_schema, old_check_name);
        END IF;
    END
$$;

alter table inventory_levels
    drop column reorder_quantity;

alter table inventory_levels
    add constraint ck_inventory_levels_quantities
        check (
            on_hand_quantity >= 0
                and committed_quantity >= 0
                and (par_quantity is null or par_quantity >= 0)
                and (calculated_reorder_point is null or calculated_reorder_point >= 0)
                and (manual_reorder_point is null or manual_reorder_point >= 0)
            );
