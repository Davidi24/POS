create table devices (
    id uuid not null,
    restaurant_id uuid not null,
    branch_id uuid,
    code varchar(50) not null,
    name varchar(100) not null,
    device_type varchar(30) not null,
    manufacturer varchar(100),
    model varchar(100),
    serial_number varchar(100),
    platform varchar(50),
    os_version varchar(50),
    app_version varchar(50),
    status varchar(30) not null,
    is_active boolean not null,
    is_online boolean not null,
    auth_secret_hash text,
    auth_secret_rotated_at timestamptz,
    last_seen_at timestamptz,
    ip_address inet,
    mac_address varchar(50),
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid references users(id),
    updated_by uuid references users(id),
    primary key (id),
    constraint uk_devices_restaurant_code unique (restaurant_id, code),
    constraint fk_devices_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_devices_branch foreign key (branch_id) references branches(id),
    check (
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND char_length(btrim(device_type)) > 0
        AND char_length(btrim(status)) > 0
    )
);
create index idx_devices_restaurant_id on devices (restaurant_id);
create index idx_devices_branch_id on devices (branch_id);
create index idx_devices_device_type on devices (device_type);
create index idx_devices_status on devices (status);
create index idx_devices_last_seen_at on devices (last_seen_at);
create index idx_devices_serial_number on devices (serial_number);
create index idx_devices_mac_address on devices (mac_address);
create index idx_devices_created_by on devices (created_by);
create index idx_devices_updated_by on devices (updated_by);

create table "device-assignments" (
    id uuid not null,
    device_id uuid not null,
    branch_id uuid,
    user_id uuid references users(id),
    assignment_type varchar(30) not null,
    assigned_at timestamptz not null,
    unassigned_at timestamptz,
    is_active boolean not null,
    assigned_by uuid references users(id),
    notes text,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint fk_device_assignments_device foreign key (device_id) references devices(id),
    constraint fk_device_assignments_branch foreign key (branch_id) references branches(id),
    check (
        char_length(btrim(assignment_type)) > 0
        AND (branch_id IS NOT NULL OR user_id IS NOT NULL)
        AND (unassigned_at IS NULL OR unassigned_at > assigned_at)
    )
);
create index idx_device_assignments_device_id on "device-assignments" (device_id);
create index idx_device_assignments_branch_id on "device-assignments" (branch_id);
create index idx_device_assignments_user_id on "device-assignments" (user_id);
create index idx_device_assignments_assigned_by on "device-assignments" (assigned_by);
create index idx_device_assignments_is_active on "device-assignments" (is_active);

create table "device-pairing-tokens" (
    id uuid not null,
    device_id uuid not null,
    token_hash text not null,
    expires_at timestamptz not null,
    used_at timestamptz,
    requested_ip inet,
    created_at timestamptz not null,
    created_by uuid references users(id),
    primary key (id),
    constraint uk_device_pairing_tokens_token_hash unique (token_hash),
    constraint fk_device_pairing_tokens_device foreign key (device_id) references devices(id),
    check (
        expires_at > created_at
        AND (used_at IS NULL OR used_at >= created_at)
    )
);
create index idx_device_pairing_tokens_device_id on "device-pairing-tokens" (device_id);
create index idx_device_pairing_tokens_expires_at on "device-pairing-tokens" (expires_at);
create index idx_device_pairing_tokens_created_by on "device-pairing-tokens" (created_by);

create table "device-printer-profiles" (
    id uuid not null,
    device_id uuid not null,
    connection_type varchar(30) not null check (connection_type in ('NETWORK', 'USB', 'BLUETOOTH', 'SERIAL')),
    paper_width_mm integer not null,
    printer_ip inet,
    printer_port integer,
    auto_cut boolean not null,
    cash_drawer_kick_enabled boolean not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    primary key (id),
    constraint uk_device_printer_profiles_device_id unique (device_id),
    constraint fk_device_printer_profiles_device foreign key (device_id) references devices(id),
    check (
        paper_width_mm > 0
        AND (printer_port IS NULL OR (printer_port BETWEEN 1 AND 65535))
        AND (
            connection_type <> 'NETWORK'
            OR (printer_ip IS NOT NULL AND printer_port IS NOT NULL)
        )
    )
);
