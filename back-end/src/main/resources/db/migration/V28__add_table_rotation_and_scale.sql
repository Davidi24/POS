alter table tables
    add column rotation_degrees numeric(7, 3) not null default 0,
    add column layout_scale numeric(6, 4) not null default 0.7400;

alter table tables
    add constraint ck_tables_rotation_degrees
        check (rotation_degrees >= 0 and rotation_degrees < 360),
    add constraint ck_tables_layout_scale
        check (layout_scale >= 0.25 and layout_scale <= 4.00);
