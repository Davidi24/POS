-- Tills, tablets and screens only on the restaurant's network (user, 2026-11-24): each branch's own networks as the server
-- sees them (an internet address, or the /64 of an IPv6 one), and the switch. While it's on, connecting such a device and
-- signing in with a PIN on a till work only from one of them; owners and the platform's admins still connect from anywhere.
CREATE TABLE device_network_rules (
    branch_id uuid PRIMARY KEY REFERENCES branches (id),
    restaurant_id uuid NOT NULL REFERENCES restaurants (id),
    only_on_network boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL,
    updated_by uuid REFERENCES users (id)
);

CREATE TABLE device_networks (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches (id),
    address varchar(64) NOT NULL,
    learned_from_device_id uuid REFERENCES devices (id) ON DELETE SET NULL,
    learned_from_name varchar(150),
    added_by uuid REFERENCES users (id),
    added_at timestamptz NOT NULL,
    last_seen_at timestamptz,
    CONSTRAINT uk_device_networks_branch_address UNIQUE (branch_id, address)
);

-- Who tried from another network (kept a week), so the owner can see it and allow that network from anywhere: the
-- restaurant's internet address can change, and then the tills stop until it's allowed.
CREATE TABLE device_network_refusals (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches (id),
    address varchar(64) NOT NULL,
    device_id uuid REFERENCES devices (id) ON DELETE SET NULL,
    device_name varchar(150),
    kind varchar(20) NOT NULL CHECK (kind IN ('CONNECT', 'PIN')),
    refused_at timestamptz NOT NULL
);
CREATE INDEX idx_device_network_refusals_branch ON device_network_refusals (branch_id, refused_at DESC);
