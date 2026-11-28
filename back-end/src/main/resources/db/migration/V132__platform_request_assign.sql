-- Requests to the Super Admin (user, 2026-11-21: "I can mark it solved if I want, or assign it to someone"):
-- SOLVED = taken care of without the app doing anything; a request can be given to a support admin, who then sees it.
ALTER TABLE platform_requests DROP CONSTRAINT IF EXISTS platform_requests_status_check;
ALTER TABLE platform_requests ADD CONSTRAINT platform_requests_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'DECLINED', 'SOLVED'));

ALTER TABLE platform_requests ADD COLUMN assigned_to uuid REFERENCES users (id);
ALTER TABLE platform_requests ADD COLUMN assigned_by uuid REFERENCES users (id);
ALTER TABLE platform_requests ADD COLUMN assigned_at timestamptz;
ALTER TABLE platform_requests ADD COLUMN assign_note varchar(2000);
CREATE INDEX idx_platform_requests_assigned ON platform_requests (assigned_to, status);
