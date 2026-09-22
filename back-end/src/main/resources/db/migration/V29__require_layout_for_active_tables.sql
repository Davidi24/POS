update tables
set is_active = false
where floor is null
   or position_x is null
   or position_y is null;

alter table tables
    add constraint ck_tables_active_layout
        check (
            is_active = false
            or (
                floor is not null
                and char_length(btrim(floor)) > 0
                and position_x is not null
                and position_y is not null
            )
        );
