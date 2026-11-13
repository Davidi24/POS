-- Each built-in role sees only its own workspace (user, 2026-10-30: "the waiter should see only POS, Kitchen only KDS"):
-- the Waiter no longer holds a Kitchen display permission (POS → Kitchen Status reads the kitchen with ORDER_READ), and
-- Kitchen no longer holds POS permissions (the KDS reads menus and pre-orders with KDS_READ). Role seeding only ever adds
-- permissions, so they're taken away here.
DELETE FROM role_permissions rp
USING roles r, permissions p
WHERE rp.role_id = r.id AND rp.permission_id = p.id AND r.is_system
  AND ((r.code = 'WAITER' AND p.code = 'KDS_READ')
    OR (r.code = 'KITCHEN' AND p.code IN ('MENUS_READ', 'ORDER_READ')));
