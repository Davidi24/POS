create table auth_email_verification_tokens (
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    id uuid not null,
    user_id uuid not null,
    token_hash text not null,
    primary key (id),
    constraint uk_auth_email_verification_tokens_token_hash unique (token_hash),
    check (expires_at > created_at)
);

create table auth_login_attempts (
    success boolean not null,
    attempted_at timestamptz not null,
    id uuid not null,
    user_id uuid,
    failure_reason varchar(50) check (failure_reason in ('INVALID_CREDENTIALS', 'ACCOUNT_LOCKED', 'ACCOUNT_INACTIVE', 'EMAIL_NOT_VERIFIED', 'ACCOUNT_RATE_LIMITED', 'IP_RATE_LIMITED')),
    identifier varchar(150),
    ip_address varchar(255),
    user_agent text,
    primary key (id)
);

create table auth_password_reset_tokens (
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    id uuid not null,
    user_id uuid not null,
    token_hash text not null,
    primary key (id),
    constraint uk_auth_password_reset_tokens_token_hash unique (token_hash),
    check (expires_at > created_at)
);

create table auth_sms_otp_codes (
    failed_attempts integer not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    id uuid not null,
    user_id uuid not null,
    purpose varchar(30) not null check (purpose in ('PASSWORD_RESET', 'PHONE_VERIFICATION')),
    phone_number_snapshot varchar(50) not null,
    code_hash text not null,
    primary key (id),
    check (expires_at > created_at AND failed_attempts >= 0)
);

create table "branch-addresses" (
    is_primary boolean not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    updated_at timestamptz not null,
    branch_id uuid not null,
    created_by uuid,
    id uuid not null,
    updated_by uuid,
    postal_code varchar(20),
    address_type varchar(30) not null check (address_type in ('LEGAL', 'BILLING', 'HEAD_OFFICE', 'SHIPPING', 'PHYSICAL')),
    city varchar(100) not null,
    country varchar(100) not null,
    street_line_1 varchar(255) not null,
    street_line_2 varchar(255),
    primary key (id),
    check (
        char_length(btrim(country)) > 0
        AND char_length(btrim(city)) > 0
        AND char_length(btrim(street_line_1)) > 0
        AND address_type IN ('LEGAL', 'BILLING', 'HEAD_OFFICE', 'SHIPPING', 'PHYSICAL')
    )
);

create table "branch-contacts" (
    is_primary boolean not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    updated_at timestamptz not null,
    branch_id uuid not null,
    created_by uuid,
    id uuid not null,
    updated_by uuid,
    contact_type varchar(30) not null check (contact_type in ('GENERAL', 'OWNER', 'MANAGER', 'ACCOUNTING', 'SUPPORT')),
    phone varchar(50),
    job_title varchar(100),
    email varchar(150),
    full_name varchar(150) not null,
    primary key (id),
    check (
        char_length(btrim(full_name)) > 0
        AND contact_type IN ('GENERAL', 'OWNER', 'MANAGER', 'ACCOUNTING', 'SUPPORT')
    )
);

create table branches (
    is_active boolean not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    updated_at timestamptz not null,
    created_by uuid,
    id uuid not null,
    manager_user_id uuid,
    restaurant_id uuid not null,
    updated_by uuid,
    status varchar(30) not null check (status in ('ACTIVE', 'INACTIVE', 'TEMPORARILY_CLOSED', 'ARCHIVED')),
    phone varchar(50),
    code varchar(100) not null,
    email varchar(150),
    name varchar(150) not null,
    description text,
    primary key (id),
    constraint uk_branches_restaurant_code unique (restaurant_id, code),
    check (
        char_length(btrim(name)) > 0
        AND char_length(btrim(code)) > 0
        AND status IN ('ACTIVE', 'INACTIVE', 'TEMPORARILY_CLOSED', 'ARCHIVED')
    )
);

create table permissions (
    created_at timestamptz not null,
    id uuid not null,
    code varchar(100) not null,
    name varchar(150) not null,
    description text,
    primary key (id),
    constraint uk_permissions_code unique (code)
);

create table "restaurant-addresses" (
    is_primary boolean not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    updated_at timestamptz not null,
    created_by uuid,
    id uuid not null,
    restaurant_id uuid not null,
    updated_by uuid,
    postal_code varchar(20),
    address_type varchar(30) not null check (address_type in ('LEGAL', 'BILLING', 'HEAD_OFFICE', 'SHIPPING', 'PHYSICAL')),
    city varchar(100) not null,
    country varchar(100) not null,
    street_line_1 varchar(255) not null,
    street_line_2 varchar(255),
    primary key (id),
    check (
        char_length(btrim(country)) > 0
        AND char_length(btrim(city)) > 0
        AND char_length(btrim(street_line_1)) > 0
        AND address_type IN ('LEGAL', 'BILLING', 'HEAD_OFFICE', 'SHIPPING', 'PHYSICAL')
    )
);

create table "restaurant-branding" (
    created_at timestamptz not null,
    deleted_at timestamptz,
    updated_at timestamptz not null,
    created_by uuid,
    id uuid not null,
    restaurant_id uuid not null unique,
    updated_by uuid,
    primary_color varchar(20),
    secondary_color varchar(20),
    logo_url text,
    receipt_footer text,
    receipt_header text,
    primary key (id),
    check (
        (primary_color IS NULL OR char_length(btrim(primary_color)) > 0)
        AND (secondary_color IS NULL OR char_length(btrim(secondary_color)) > 0)
    )
);

create table "restaurant-contacts" (
    is_primary boolean not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    updated_at timestamptz not null,
    created_by uuid,
    id uuid not null,
    restaurant_id uuid not null,
    updated_by uuid,
    contact_type varchar(30) not null check (contact_type in ('GENERAL', 'OWNER', 'MANAGER', 'ACCOUNTING', 'SUPPORT')),
    phone varchar(50),
    job_title varchar(100),
    email varchar(150),
    full_name varchar(150) not null,
    primary key (id),
    check (
        char_length(btrim(full_name)) > 0
        AND contact_type IN ('GENERAL', 'OWNER', 'MANAGER', 'ACCOUNTING', 'SUPPORT')
    )
);

create table "restaurant-tax-profiles" (
    is_default boolean not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    effective_from timestamptz,
    effective_to timestamptz,
    updated_at timestamptz not null,
    created_by uuid,
    id uuid not null,
    restaurant_id uuid not null,
    updated_by uuid,
    country varchar(100) not null,
    fiscal_code varchar(100),
    tax_number varchar(100),
    vat_number varchar(100),
    tax_office varchar(150),
    primary key (id),
    check (
        char_length(btrim(country)) > 0
        AND (effective_from IS NULL OR effective_to IS NULL OR effective_to > effective_from)
    )
);

create table restaurants (
    currency varchar(3) not null,
    is_active boolean not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    updated_at timestamptz not null,
    created_by uuid,
    id uuid not null,
    owner_id uuid,
    updated_by uuid,
    status varchar(30) not null check (status in ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')),
    phone varchar(50),
    code varchar(100) not null,
    timezone varchar(100) not null,
    email varchar(150),
    name varchar(150) not null,
    slug varchar(150) not null,
    legal_name varchar(200) not null,
    description text,
    website varchar(255),
    primary key (id),
    constraint uk_restaurants_code unique (code),
    constraint uk_restaurants_slug unique (slug),
    check (
        char_length(btrim(name)) > 0
        AND char_length(btrim(legal_name)) > 0
        AND char_length(btrim(code)) > 0
        AND char_length(btrim(slug)) > 0
        AND char_length(currency) = 3
        AND status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')
    )
);

create table role_permissions (
    created_at timestamptz not null,
    id uuid not null,
    permission_id uuid not null,
    role_id uuid not null,
    primary key (id),
    constraint uk_role_permissions_role_permission unique (role_id, permission_id)
);

create table roles (
    is_active boolean not null,
    is_assignable boolean not null,
    is_protected boolean not null,
    is_system boolean not null,
    created_at timestamptz not null,
    rank bigint not null,
    updated_at timestamptz not null,
    id uuid not null,
    code varchar(50) not null,
    name varchar(100) not null,
    description text,
    primary key (id),
    constraint uk_roles_code unique (code),
    constraint uk_roles_name unique (name),
    check (rank > 0)
);

create table user_roles (
    assigned_at timestamptz not null,
    assigned_by uuid,
    id uuid not null,
    role_id uuid not null,
    user_id uuid not null,
    primary key (id),
    constraint uk_user_roles_user_role unique (user_id, role_id)
);

create table user_sessions (
    revoked boolean not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    last_used_at timestamptz,
    revoked_at timestamptz,
    id uuid not null,
    token_id uuid not null,
    user_id uuid not null,
    session_type varchar(30) not null check (session_type in ('PASSWORD')),
    ip_address varchar(45),
    device_name varchar(100),
    revoked_reason varchar(100),
    refresh_token_hash text not null,
    user_agent text,
    primary key (id),
    constraint uk_user_sessions_token_id unique (token_id),
    check ((revoked = false) OR (revoked = true AND revoked_at IS NOT NULL))
);

create table users (
    email_verified boolean not null,
    failed_login_attempts integer not null,
    is_active boolean not null,
    phone_verified boolean not null,
    pin_attempts integer not null,
    pin_enabled boolean not null,
    created_at timestamptz not null,
    deleted_at timestamptz,
    email_verified_at timestamptz,
    last_login_at timestamptz,
    locked_until timestamptz,
    password_updated_at timestamptz,
    phone_verified_at timestamptz,
    pin_locked_until timestamptz,
    updated_at timestamptz not null,
    version bigint,
    created_by uuid,
    default_branch_id uuid,
    id uuid not null,
    restaurant_id uuid,
    updated_by uuid,
    status varchar(30) not null,
    last_login_ip varchar(45),
    normalized_phone varchar(50),
    phone varchar(50),
    username varchar(50),
    first_name varchar(100) not null,
    last_name varchar(100) not null,
    email varchar(150) not null,
    avatar_url text,
    password_hash text not null,
    pin_hash text,
    primary key (id),
    constraint uk_users_email unique (email),
    constraint uk_users_username unique (username),
    constraint uk_users_normalized_phone unique (normalized_phone),
    check (pin_attempts >= 0 AND failed_login_attempts >= 0)
);

create index idx_auth_email_verification_tokens_user_id on auth_email_verification_tokens (user_id);
create index idx_auth_email_verification_tokens_expires_at on auth_email_verification_tokens (expires_at);
create index idx_auth_email_verification_tokens_used_at on auth_email_verification_tokens (used_at);
create index idx_auth_login_attempts_identifier on auth_login_attempts (identifier);
create index idx_auth_login_attempts_ip_address on auth_login_attempts (ip_address);
create index idx_auth_login_attempts_attempted_at on auth_login_attempts (attempted_at);
create index idx_auth_login_attempts_success on auth_login_attempts (success);
create index idx_auth_login_attempts_user_id on auth_login_attempts (user_id);
create index idx_auth_password_reset_tokens_user_id on auth_password_reset_tokens (user_id);
create index idx_auth_password_reset_tokens_expires_at on auth_password_reset_tokens (expires_at);
create index idx_auth_password_reset_tokens_used_at on auth_password_reset_tokens (used_at);
create index idx_auth_sms_otp_codes_user_purpose on auth_sms_otp_codes (user_id, purpose);
create index idx_auth_sms_otp_codes_expires_at on auth_sms_otp_codes (expires_at);
create index idx_auth_sms_otp_codes_used_at on auth_sms_otp_codes (used_at);
create index idx_branch_addresses_branch_id on "branch-addresses" (branch_id);
create index idx_branch_addresses_type on "branch-addresses" (address_type);
create index idx_branch_addresses_is_primary on "branch-addresses" (is_primary);
create index idx_branch_addresses_created_by on "branch-addresses" (created_by);
create index idx_branch_addresses_updated_by on "branch-addresses" (updated_by);
create index idx_branch_addresses_deleted_at on "branch-addresses" (deleted_at);
create index idx_branch_contacts_branch_id on "branch-contacts" (branch_id);
create index idx_branch_contacts_type on "branch-contacts" (contact_type);
create index idx_branch_contacts_is_primary on "branch-contacts" (is_primary);
create index idx_branch_contacts_created_by on "branch-contacts" (created_by);
create index idx_branch_contacts_updated_by on "branch-contacts" (updated_by);
create index idx_branch_contacts_deleted_at on "branch-contacts" (deleted_at);
create index idx_branches_restaurant_id on branches (restaurant_id);
create index idx_branches_status on branches (status);
create index idx_branches_manager_user_id on branches (manager_user_id);
create index idx_branches_created_by on branches (created_by);
create index idx_branches_updated_by on branches (updated_by);
create index idx_branches_deleted_at on branches (deleted_at);
create index idx_restaurant_addresses_restaurant_id on "restaurant-addresses" (restaurant_id);
create index idx_restaurant_addresses_type on "restaurant-addresses" (address_type);
create index idx_restaurant_addresses_is_primary on "restaurant-addresses" (is_primary);
create index idx_restaurant_addresses_created_by on "restaurant-addresses" (created_by);
create index idx_restaurant_addresses_updated_by on "restaurant-addresses" (updated_by);
create index idx_restaurant_addresses_deleted_at on "restaurant-addresses" (deleted_at);
create index idx_restaurant_branding_restaurant_id on "restaurant-branding" (restaurant_id);
create index idx_restaurant_branding_created_by on "restaurant-branding" (created_by);
create index idx_restaurant_branding_updated_by on "restaurant-branding" (updated_by);
create index idx_restaurant_branding_deleted_at on "restaurant-branding" (deleted_at);
create index idx_restaurant_contacts_restaurant_id on "restaurant-contacts" (restaurant_id);
create index idx_restaurant_contacts_type on "restaurant-contacts" (contact_type);
create index idx_restaurant_contacts_is_primary on "restaurant-contacts" (is_primary);
create index idx_restaurant_contacts_created_by on "restaurant-contacts" (created_by);
create index idx_restaurant_contacts_updated_by on "restaurant-contacts" (updated_by);
create index idx_restaurant_contacts_deleted_at on "restaurant-contacts" (deleted_at);
create index idx_restaurant_tax_profiles_restaurant_id on "restaurant-tax-profiles" (restaurant_id);
create index idx_restaurant_tax_profiles_is_default on "restaurant-tax-profiles" (is_default);
create index idx_restaurant_tax_profiles_effective_from on "restaurant-tax-profiles" (effective_from);
create index idx_restaurant_tax_profiles_effective_to on "restaurant-tax-profiles" (effective_to);
create index idx_restaurant_tax_profiles_created_by on "restaurant-tax-profiles" (created_by);
create index idx_restaurant_tax_profiles_updated_by on "restaurant-tax-profiles" (updated_by);
create index idx_restaurant_tax_profiles_deleted_at on "restaurant-tax-profiles" (deleted_at);
create index idx_restaurants_owner_id on restaurants (owner_id);
create index idx_restaurants_status on restaurants (status);
create index idx_restaurants_created_by on restaurants (created_by);
create index idx_restaurants_updated_by on restaurants (updated_by);
create index idx_restaurants_deleted_at on restaurants (deleted_at);
create index idx_role_permissions_role_id on role_permissions (role_id);
create index idx_role_permissions_permission_id on role_permissions (permission_id);
create index idx_roles_rank on roles (rank);
create index idx_roles_active_rank on roles (is_active, rank);
create index idx_user_roles_user_id on user_roles (user_id);
create index idx_user_roles_role_id on user_roles (role_id);
create index idx_user_roles_assigned_by on user_roles (assigned_by);
create index idx_user_sessions_user_id on user_sessions (user_id);
create index idx_user_sessions_expires_at on user_sessions (expires_at);
create index idx_user_sessions_refresh_token_hash on user_sessions (refresh_token_hash);
create index idx_user_sessions_token_id on user_sessions (token_id);
create index idx_user_sessions_revoked on user_sessions (revoked);
create index idx_users_restaurant_id on users (restaurant_id);
create index idx_users_default_branch_id on users (default_branch_id);
create index idx_users_status on users (status);
create index idx_users_email_verified on users (email_verified);
create index idx_users_phone_verified on users (phone_verified);
create index idx_users_locked_until on users (locked_until);
create index idx_users_created_by on users (created_by);
create index idx_users_updated_by on users (updated_by);

alter table if exists "branch-addresses" add constraint fk_branch_addresses_branch foreign key (branch_id) references branches;
alter table if exists "branch-contacts" add constraint fk_branch_contacts_branch foreign key (branch_id) references branches;
alter table if exists branches add constraint fk_branches_restaurant foreign key (restaurant_id) references restaurants;
alter table if exists "restaurant-addresses" add constraint fk_restaurant_addresses_restaurant foreign key (restaurant_id) references restaurants;
alter table if exists "restaurant-branding" add constraint fk_restaurant_branding_restaurant foreign key (restaurant_id) references restaurants;
alter table if exists "restaurant-contacts" add constraint fk_restaurant_contacts_restaurant foreign key (restaurant_id) references restaurants;
alter table if exists "restaurant-tax-profiles" add constraint fk_restaurant_tax_profiles_restaurant foreign key (restaurant_id) references restaurants;
