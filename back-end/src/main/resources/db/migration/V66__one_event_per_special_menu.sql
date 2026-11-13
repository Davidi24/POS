-- A special menu belongs to at most one event night: the event owns the menu's dates, so two events sharing a menu
-- would overwrite each other's days. Where that already happened, the event changed last keeps the menu.
UPDATE restaurant_events e
SET menu_id = NULL
WHERE e.menu_id IS NOT NULL
  AND EXISTS (
      SELECT 1
      FROM restaurant_events other
      WHERE other.menu_id = e.menu_id
        AND other.id <> e.id
        AND (other.updated_at > e.updated_at OR (other.updated_at = e.updated_at AND other.id > e.id))
  );

CREATE UNIQUE INDEX uk_restaurant_events_menu
    ON restaurant_events (menu_id)
    WHERE menu_id IS NOT NULL;
