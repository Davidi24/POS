create table "settings-audit-logs" (
    id uuid primary key,
    restaurant_id uuid not null,
    branch_id uuid,
    entity_type varchar(50) not null,
    entity_id uuid,
    action varchar(50) not null,
    message varchar(500) not null,
    actor_user_id uuid,
    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint fk_settings_audit_logs_restaurant foreign key (restaurant_id) references restaurants(id),
    constraint fk_settings_audit_logs_branch foreign key (branch_id) references branches(id),
    constraint fk_settings_audit_logs_actor foreign key (actor_user_id) references users(id),
    constraint chk_settings_audit_logs_entity_type_non_blank check (char_length(btrim(entity_type)) > 0),
    constraint chk_settings_audit_logs_action_non_blank check (char_length(btrim(action)) > 0),
    constraint chk_settings_audit_logs_message_non_blank check (char_length(btrim(message)) > 0)
);

create index idx_settings_audit_logs_restaurant_id on "settings-audit-logs" (restaurant_id);
create index idx_settings_audit_logs_branch_id on "settings-audit-logs" (branch_id);
create index idx_settings_audit_logs_entity_type on "settings-audit-logs" (entity_type);
create index idx_settings_audit_logs_action on "settings-audit-logs" (action);
create index idx_settings_audit_logs_actor_user_id on "settings-audit-logs" (actor_user_id);
create index idx_settings_audit_logs_created_at on "settings-audit-logs" (created_at);
