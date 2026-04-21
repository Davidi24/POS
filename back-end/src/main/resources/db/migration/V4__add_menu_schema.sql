create table "option-group-types" (
    id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    code varchar(50) not null,
    name varchar(120) not null,
    description text,
    primary key (id)
);
alter table "option-group-types" add constraint uk_option_group_type_code unique (code);
alter table "option-group-types" add constraint uk_option_group_type_name unique (name);
create index idx_option_group_type_code on "option-group-types" (code);
create index idx_option_group_type_name on "option-group-types" (name);

create table menus (
    id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    restaurant_id uuid not null references restaurants(id),
    created_by uuid references users(id),
    updated_by uuid references users(id),
    code varchar(50) not null,
    name varchar(150) not null,
    description text,
    is_active boolean not null,
    display_order integer not null,
    primary key (id)
);
alter table menus add constraint uk_menus_restaurant_code unique (restaurant_id, code);
create index idx_menus_restaurant_id on menus (restaurant_id);
create index idx_menus_created_by on menus (created_by);
create index idx_menus_updated_by on menus (updated_by);

create table "menu-sections" (
    id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    menu_id uuid not null references menus(id),
    name varchar(150) not null,
    description text,
    display_order integer not null,
    is_active boolean not null,
    primary key (id)
);
alter table "menu-sections" add constraint uk_menu_sections_menu_name unique (menu_id, name);
create index idx_menu_sections_menu_id on "menu-sections" (menu_id);

create table "menu-items" (
    id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    section_id uuid not null references "menu-sections"(id),
    sku varchar(80),
    name varchar(150) not null,
    description text,
    base_price numeric(19, 2) not null,
    image_url text,
    is_available boolean not null,
    display_order integer not null,
    primary key (id)
);
create index idx_menu_items_section_id on "menu-items" (section_id);
create index idx_menu_items_sku on "menu-items" (sku);

create table "menu-variants" (
    id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    menu_item_id uuid not null references "menu-items"(id),
    name varchar(120) not null,
    sku varchar(80),
    price_delta numeric(19, 2),
    is_default boolean not null,
    is_active boolean not null,
    display_order integer not null,
    primary key (id)
);
alter table "menu-variants" add constraint uk_menu_variants_menu_item_name unique (menu_item_id, name);
create index idx_menu_variants_menu_item_id on "menu-variants" (menu_item_id);

create table "option-groups" (
    id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    restaurant_id uuid not null references restaurants(id),
    type_id uuid not null references "option-group-types"(id),
    name varchar(150) not null,
    description text,
    min_select integer,
    max_select integer,
    is_required boolean not null,
    display_order integer not null,
    is_active boolean not null,
    primary key (id)
);
alter table "option-groups" add constraint uk_option_groups_restaurant_name unique (restaurant_id, name);
create index idx_option_groups_restaurant_id on "option-groups" (restaurant_id);
create index idx_option_groups_type_id on "option-groups" (type_id);

create table "option-items" (
    id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    option_group_id uuid not null references "option-groups"(id),
    code varchar(50),
    name varchar(150) not null,
    price_delta numeric(19, 2),
    is_available boolean not null,
    display_order integer not null,
    primary key (id)
);
alter table "option-items" add constraint uk_option_items_group_name unique (option_group_id, name);
create index idx_option_items_option_group_id on "option-items" (option_group_id);

create table menu_item_option_groups (
    id uuid not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    menu_item_id uuid not null references "menu-items"(id),
    option_group_id uuid not null references "option-groups"(id),
    display_order integer not null,
    min_select_override integer,
    max_select_override integer,
    is_required_override boolean,
    primary key (id)
);
alter table menu_item_option_groups add constraint uk_menu_item_option_groups_item_group unique (menu_item_id, option_group_id);
create index idx_menu_item_option_groups_menu_item_id on menu_item_option_groups (menu_item_id);
create index idx_menu_item_option_groups_option_group_id on menu_item_option_groups (option_group_id);