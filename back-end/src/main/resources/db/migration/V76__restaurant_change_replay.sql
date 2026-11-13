CREATE TABLE restaurant_change_replays (
 restaurant_id uuid NOT NULL REFERENCES restaurants(id),
 operation varchar(40) NOT NULL,
 request_key varchar(64) NOT NULL,
 request_hash varchar(64) NOT NULL,
 response_json text NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY (restaurant_id,operation,request_key)
);
