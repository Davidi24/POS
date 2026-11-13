CREATE TABLE stored_image_cleanup (
    object_key text PRIMARY KEY,
    image_url text NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    last_error varchar(500),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_stored_image_cleanup_due ON stored_image_cleanup (next_attempt_at);
