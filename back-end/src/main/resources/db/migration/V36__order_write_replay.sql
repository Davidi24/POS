CREATE TABLE order_write_requests (
 owner_id varchar(200) NOT NULL, request_key varchar(100) NOT NULL,
 fingerprint varchar(64) NOT NULL, status integer NOT NULL, content_type varchar(200),
 body bytea NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(owner_id, request_key)
);
CREATE INDEX idx_order_write_requests_created ON order_write_requests(created_at);
