-- Reservation policy settings for the Admin Hub (defaults are the rules agreed with the owner, 2026-09-27).
alter table settings add column large_group_from integer not null default 5;
alter table settings add column large_group_extra_minutes integer not null default 15;
alter table settings add column approval_group_size integer not null default 7;
alter table settings add column hold_minutes integer not null default 30;
alter table settings add column hold_warning_minutes integer not null default 20;
alter table settings add column check_in_opens_minutes integer not null default 120;
alter table settings add column confirm_reminder_time time not null default '15:00';
alter table settings add column same_day_confirm_minutes integer not null default 120;
alter table settings add column attendance_call_minutes integer not null default 120;
alter table settings add column reopen_window_minutes integer not null default 60;
alter table settings add column undo_seat_minutes integer not null default 15;
alter table settings add column running_late_max_minutes integer not null default 30;

alter table settings add constraint ck_settings_large_group_from check (large_group_from between 1 and 100);
alter table settings add constraint ck_settings_large_group_extra_minutes check (large_group_extra_minutes between 0 and 240);
alter table settings add constraint ck_settings_approval_group_size check (approval_group_size between 1 and 200);
alter table settings add constraint ck_settings_hold_minutes check (hold_minutes between 5 and 240);
alter table settings add constraint ck_settings_hold_warning_minutes check (hold_warning_minutes between 0 and 240 and hold_warning_minutes < hold_minutes);
alter table settings add constraint ck_settings_check_in_opens_minutes check (check_in_opens_minutes between 0 and 1440);
alter table settings add constraint ck_settings_same_day_confirm_minutes check (same_day_confirm_minutes between 0 and 1440);
alter table settings add constraint ck_settings_attendance_call_minutes check (attendance_call_minutes between 0 and 1440);
alter table settings add constraint ck_settings_reopen_window_minutes check (reopen_window_minutes between 0 and 1440);
alter table settings add constraint ck_settings_undo_seat_minutes check (undo_seat_minutes between 0 and 240);
alter table settings add constraint ck_settings_running_late_max_minutes check (running_late_max_minutes between 0 and 240);

-- Agreed default: pre-orders go to the kitchen 30 minutes before (was 15). Only rows still on the old default move.
alter table settings alter column pre_order_lead_minutes set default 30;
update settings set pre_order_lead_minutes = 30 where pre_order_lead_minutes = 15;
