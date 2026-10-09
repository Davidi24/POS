create table floor_layouts (
                               id uuid not null,
                               restaurant_id uuid not null,
                               branch_id uuid not null,
                               floor_name varchar(50) not null,
                               plan_image_key varchar(512),
                               plan_offset_x numeric(10, 4) not null default 0,
                               plan_offset_y numeric(10, 4) not null default 0,
                               plan_scale numeric(6, 4) not null default 1,
                               created_at timestamptz not null,
                               updated_at timestamptz not null,
                               created_by uuid references users(id),
                               updated_by uuid references users(id),

                               primary key (id),

                               constraint uk_floor_layouts_branch_floor
                                   unique (branch_id, floor_name),

                               constraint fk_floor_layouts_restaurant
                                   foreign key (restaurant_id)
                                       references restaurants(id),

                               constraint fk_floor_layouts_branch
                                   foreign key (branch_id)
                                       references branches(id),

                               constraint ck_floor_layouts_floor_name
                                   check (char_length(btrim(floor_name)) > 0),

                               constraint ck_floor_layouts_plan_scale
                                   check (plan_scale >= 0.25 and plan_scale <= 4.00)
);

create index idx_floor_layouts_restaurant_id
    on floor_layouts (restaurant_id);

create index idx_floor_layouts_branch_id
    on floor_layouts (branch_id);