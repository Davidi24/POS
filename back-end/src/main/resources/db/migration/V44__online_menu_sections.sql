-- The online menu's own sections (one online menu per restaurant, it exists implicitly). Dishes aren't copied:
-- a dish keeps its staff section and may also sit in one online section. No online section = staff menu only.
create table online_menu_sections (
    id uuid not null,
    restaurant_id uuid not null,
    name varchar(150) not null,
    display_order integer not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,
    primary key (id),
    constraint fk_online_menu_sections_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_online_menu_sections_created_by_user foreign key (created_by) references users(id),
    constraint fk_online_menu_sections_updated_by_user foreign key (updated_by) references users(id),
    check (char_length(btrim(name)) > 0 and display_order >= 0)
);
create unique index uk_online_menu_sections_restaurant_name on online_menu_sections (restaurant_id, lower(name));
create index idx_online_menu_sections_restaurant_id on online_menu_sections (restaurant_id);

alter table "menu-items" add column online_section_id uuid;
alter table "menu-items" add constraint fk_menu_items_online_section foreign key (online_section_id) references online_menu_sections(id);
create index idx_menu_items_online_section_id on "menu-items" (online_section_id);

-- "Show in online menu" is now "has an online section"; the V43 flag was never switched on anywhere yet.
alter table "menu-items" drop column show_online;
