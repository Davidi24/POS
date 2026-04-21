alter table "option-group-types"
    add constraint ck_option_group_types_valid
        check (
            char_length(btrim(code)) > 0
            and char_length(btrim(name)) > 0
        );

alter table menus
    add constraint ck_menus_valid
        check (
            char_length(btrim(code)) > 0
            and char_length(btrim(name)) > 0
            and display_order >= 0
        );

alter table "menu-sections"
    add constraint ck_menu_sections_valid
        check (
            char_length(btrim(name)) > 0
            and display_order >= 0
        );

alter table "menu-items"
    add constraint ck_menu_items_valid
        check (
            char_length(btrim(name)) > 0
            and (sku is null or char_length(btrim(sku)) > 0)
            and base_price >= 0
            and display_order >= 0
        );

alter table "menu-variants"
    add constraint ck_menu_variants_valid
        check (
            char_length(btrim(name)) > 0
            and (sku is null or char_length(btrim(sku)) > 0)
            and display_order >= 0
        );

alter table "option-groups"
    add constraint ck_option_groups_valid
        check (
            char_length(btrim(name)) > 0
            and display_order >= 0
            and (min_select is null or min_select >= 0)
            and (max_select is null or max_select >= 0)
            and (min_select is null or max_select is null or min_select <= max_select)
        );

alter table "option-items"
    add constraint ck_option_items_valid
        check (
            char_length(btrim(name)) > 0
            and (code is null or char_length(btrim(code)) > 0)
            and display_order >= 0
        );

alter table menu_item_option_groups
    add constraint ck_menu_item_option_groups_valid
        check (
            display_order >= 0
            and (min_select_override is null or min_select_override >= 0)
            and (max_select_override is null or max_select_override >= 0)
            and (
                min_select_override is null
                or max_select_override is null
                or min_select_override <= max_select_override
            )
        );
