-- Item-specific unit conversion rules (e.g. "1 CASE = 12 BOTTLE" for one particular product).
-- Universal physical ratios (GRAM<->KILOGRAM etc.) are NOT stored here -- those are fixed
-- constants handled entirely in code (UnitConversionService), since they never change and
-- apply to every item, not just one.

create table inventory_unit_conversions (
    id                  uuid           not null,
    inventory_item_id   uuid           not null,
    from_unit           varchar(30)    not null,
    to_unit             varchar(30)    not null,
    conversion_factor   numeric(19, 6) not null,
    created_at          timestamptz    not null,
    updated_at          timestamptz    not null,
    primary key (id),
    constraint uk_inventory_unit_conversions_item_from_to unique (inventory_item_id, from_unit, to_unit),
    constraint fk_inventory_unit_conversions_inventory_item foreign key (inventory_item_id) references inventory_items (id),
    check (
        from_unit IN (
            'EACH', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER', 'OUNCE', 'POUND', 'CUP',
            'TABLESPOON', 'TEASPOON', 'PORTION', 'CASE', 'BOTTLE', 'PACK', 'TRAY'
        )
        AND to_unit IN (
            'EACH', 'GRAM', 'KILOGRAM', 'MILLILITER', 'LITER', 'OUNCE', 'POUND', 'CUP',
            'TABLESPOON', 'TEASPOON', 'PORTION', 'CASE', 'BOTTLE', 'PACK', 'TRAY'
        )
        AND conversion_factor > 0
        AND from_unit <> to_unit
    )
);

create index idx_inventory_unit_conversions_inventory_item_id on inventory_unit_conversions (inventory_item_id);
