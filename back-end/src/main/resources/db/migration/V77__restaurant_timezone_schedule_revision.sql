ALTER TABLE reservations ADD COLUMN schedule_changed_at timestamptz;
CREATE TABLE restaurant_timezone_changes (
 id uuid PRIMARY KEY, restaurant_id uuid NOT NULL REFERENCES restaurants(id),
 old_timezone varchar(100) NOT NULL, new_timezone varchar(100) NOT NULL,
 keep_booking_times boolean NOT NULL, bookings_moved integer NOT NULL,
 changed_by uuid NOT NULL REFERENCES users(id), changed_at timestamptz NOT NULL DEFAULT now()
);
