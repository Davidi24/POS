-- More reservation settings for the Admin Hub (defaults agreed with the owner, 2026-09-27).
alter table settings add column late_after_minutes integer not null default 15;
alter table settings add column guest_reminder_hours integer not null default 24;
alter table settings add column no_show_warning_from integer not null default 1;
alter table settings add column deposit_from_guests integer not null default 7;

alter table settings add constraint ck_settings_late_after_minutes check (late_after_minutes between 0 and 240 and late_after_minutes < hold_minutes);
alter table settings add constraint ck_settings_guest_reminder_hours check (guest_reminder_hours between 1 and 168);
alter table settings add constraint ck_settings_no_show_warning_from check (no_show_warning_from between 1 and 20);
alter table settings add constraint ck_settings_deposit_from_guests check (deposit_from_guests between 1 and 200);
