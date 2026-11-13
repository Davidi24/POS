ALTER TABLE restaurants ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE branches ADD COLUMN version bigint NOT NULL DEFAULT 0;
