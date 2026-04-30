alter table "settings-templates"
    drop constraint chk_settings_templates_payload_non_blank;

alter table "settings-templates"
    alter column payload type jsonb
    using payload::jsonb;

alter table "settings-templates"
    add constraint chk_settings_templates_payload_object
    check (jsonb_typeof(payload) = 'object');
