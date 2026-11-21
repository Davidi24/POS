-- Everything that happens to a request, for its history (user, 2026-11-21: "each card has a history icon with all that
-- happened to it"): sent, given to someone, taken, taken back, started, solved, an owner removed.
CREATE TABLE platform_request_events (
    id uuid PRIMARY KEY,
    seq bigserial NOT NULL,
    request_id uuid NOT NULL REFERENCES platform_requests (id) ON DELETE CASCADE,
    kind varchar(30) NOT NULL,
    actor_id uuid REFERENCES users (id),
    target_user_id uuid REFERENCES users (id),
    note varchar(2000),
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_platform_request_events_request ON platform_request_events (request_id, created_at, seq);

-- What the requests there are still show of their past.
INSERT INTO platform_request_events (id, request_id, kind, actor_id, created_at)
SELECT gen_random_uuid(), id, 'CREATED', created_by, created_at FROM platform_requests;
INSERT INTO platform_request_events (id, request_id, kind, actor_id, target_user_id, note, created_at)
SELECT gen_random_uuid(), id, CASE WHEN assigned_by = assigned_to THEN 'TAKEN' ELSE 'ASSIGNED' END, assigned_by, assigned_to, assign_note, assigned_at
FROM platform_requests WHERE assigned_to IS NOT NULL AND assigned_at IS NOT NULL;
INSERT INTO platform_request_events (id, request_id, kind, actor_id, created_at)
SELECT gen_random_uuid(), id, 'STARTED', started_by, started_at FROM platform_requests WHERE started_at IS NOT NULL;
INSERT INTO platform_request_events (id, request_id, kind, actor_id, target_user_id, note, created_at)
SELECT gen_random_uuid(), id,
       CASE WHEN status = 'APPROVED' AND kind = 'REMOVE_OWNER' THEN 'OWNER_REMOVED' WHEN status = 'APPROVED' THEN 'APPROVED'
            WHEN status = 'DECLINED' THEN 'DECLINED' ELSE 'SOLVED' END,
       decided_by, CASE WHEN status = 'APPROVED' AND kind = 'REMOVE_OWNER' THEN target_user_id END, decision_note, decided_at
FROM platform_requests WHERE status <> 'PENDING' AND decided_at IS NOT NULL;
