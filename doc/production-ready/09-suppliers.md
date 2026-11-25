# 9. Suppliers

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Inventory workspace → Suppliers (suppliers, their places, items you buy, their catalog, orders).
- Server: `/restaurants/{r}/suppliers` (suppliers, places, item links with code and price).

## How it is now
- Suppliers, their places and item links are on the server; receiving a delivery records real stock movements.

## Known gaps (MOCK, in memory)
- Orders to suppliers, price lists (catalog) and supplier pictures/banners.

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: looking at suppliers works on PIN; changing suppliers, their places and prices needs the password (purchase orders: decide here).
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
