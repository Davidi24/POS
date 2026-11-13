-- A restaurant can switch a role off (user, 2026-10-31: "I can disable a role if I want"). Its own roles use
-- roles.is_active; built-in roles are shared, so switching one off in a restaurant is kept with its pay settings here.
-- A switched-off role gives nothing there: no permissions, no pay, and it can't be handed out.
ALTER TABLE role_pay_settings ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
