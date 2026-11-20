-- Sign-in limits per device (user, 2026-11-17): the device a sign-in try came from (an id the app keeps on the device), so
-- one device's wrong tries pause only that device; and a reason for tries stopped on a paused device.
ALTER TABLE auth_login_attempts
    ADD COLUMN device_id VARCHAR(100);

CREATE INDEX idx_auth_login_attempts_device_id ON auth_login_attempts (device_id, attempted_at);

ALTER TABLE auth_login_attempts DROP CONSTRAINT IF EXISTS auth_login_attempts_failure_reason_check;
ALTER TABLE auth_login_attempts ADD CONSTRAINT auth_login_attempts_failure_reason_check CHECK (failure_reason IN (
    'INVALID_CREDENTIALS', 'ACCOUNT_LOCKED', 'ACCOUNT_INACTIVE', 'EMAIL_NOT_VERIFIED', 'ACCOUNT_RATE_LIMITED', 'IP_RATE_LIMITED',
    'DEVICE_RATE_LIMITED'));
