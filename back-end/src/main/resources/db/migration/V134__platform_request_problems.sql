-- Anyone working in a restaurant can tell the Super Admin about a problem (user, 2026-11-21: "if a user has a problem they make a
-- request and write a message"); it goes through the same tickets: open, assigned, in progress, solved.
ALTER TABLE platform_requests DROP CONSTRAINT IF EXISTS platform_requests_kind_check;
ALTER TABLE platform_requests ADD CONSTRAINT platform_requests_kind_check CHECK (kind IN ('REMOVE_OWNER', 'NEED_ADMIN', 'PROBLEM'));
CREATE INDEX idx_platform_requests_created_by ON platform_requests (created_by, status);
