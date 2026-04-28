create unique index if not exists uk_restaurant_addresses_primary_active
    on "restaurant-addresses" (restaurant_id)
    where is_primary = true and deleted_at is null;

create unique index if not exists uk_restaurant_contacts_primary_active
    on "restaurant-contacts" (restaurant_id)
    where is_primary = true and deleted_at is null;

create unique index if not exists uk_restaurant_tax_profiles_default_active
    on "restaurant-tax-profiles" (restaurant_id)
    where is_default = true and deleted_at is null;

create unique index if not exists uk_branch_addresses_primary_active
    on "branch-addresses" (branch_id)
    where is_primary = true and deleted_at is null;

create unique index if not exists uk_branch_contacts_primary_active
    on "branch-contacts" (branch_id)
    where is_primary = true and deleted_at is null;
