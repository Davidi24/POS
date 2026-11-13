-- Menus have no "Active" switch any more: whether a menu shows in POS (to waiters and at the till) is the one choice.
-- A menu that was switched off becomes active but hidden in POS.
UPDATE menus
SET shown_to_waiters = FALSE,
    is_active = TRUE
WHERE is_active = FALSE;
