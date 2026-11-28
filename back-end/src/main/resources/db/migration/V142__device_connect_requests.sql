-- A till or tablet's code typed on another device waits for the person who made the code to agree (user, 2026-11-25: "when
-- someone puts the code, it goes to the person who generated it; I agree, and in that moment the old one is removed"). The new
-- device asks, the maker agrees or refuses within a few minutes, then the new device takes the connection with its claim.
CREATE TABLE device_connect_requests (
    id uuid PRIMARY KEY,
    restaurant_id uuid NOT NULL REFERENCES restaurants (id),
    device_id uuid NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    pairing_token_id uuid NOT NULL REFERENCES "device-pairing-tokens" (id) ON DELETE CASCADE,
    requested_by uuid NOT NULL REFERENCES users (id),
    code_made_by uuid REFERENCES users (id),
    asking_device_name varchar(150),
    asking_device_system varchar(80),
    asking_address varchar(64),
    claim_hash varchar(128) NOT NULL,
    status varchar(20) NOT NULL CHECK (status IN ('WAITING', 'AGREED', 'REFUSED', 'CANCELLED', 'DONE')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    decided_at timestamptz,
    done_at timestamptz
);
CREATE INDEX idx_device_connect_requests_maker ON device_connect_requests (code_made_by, status);
CREATE INDEX idx_device_connect_requests_token ON device_connect_requests (pairing_token_id);
