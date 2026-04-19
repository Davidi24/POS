alter table roles
    add column deleted_at timestamptz;

alter table roles
    drop constraint uk_roles_code;

alter table roles
    drop constraint uk_roles_name;

create unique index uk_roles_code_active on roles (code)
    where deleted_at is null;

create unique index uk_roles_name_active on roles (name)
    where deleted_at is null;

create index idx_roles_deleted_at on roles (deleted_at);
