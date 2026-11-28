-- A request's progress (user, 2026-11-21: "show the progress of the ticket: open, assigned, in progress, solved"; no
-- declining, only solving): when someone started on it and who.
ALTER TABLE platform_requests ADD COLUMN started_at timestamptz;
ALTER TABLE platform_requests ADD COLUMN started_by uuid REFERENCES users (id);
