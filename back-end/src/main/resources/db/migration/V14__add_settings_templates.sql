create table "settings-templates" (
    id uuid primary key,
    template_name varchar(150) not null,
    description varchar(500),
    payload text not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    created_by uuid,
    updated_by uuid,

    constraint chk_settings_templates_name_non_blank check (char_length(btrim(template_name)) > 0),
    constraint chk_settings_templates_payload_non_blank check (char_length(btrim(payload)) > 0),
    constraint fk_settings_templates_created_by foreign key (created_by) references users(id),
    constraint fk_settings_templates_updated_by foreign key (updated_by) references users(id)
);

create index idx_settings_templates_created_by on "settings-templates" (created_by);
create index idx_settings_templates_updated_by on "settings-templates" (updated_by);
create index idx_settings_templates_name on "settings-templates" (template_name);
