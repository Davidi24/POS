# 8. Inventory

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Inventory workspace → Inventory (Overview, Stock, Items, Counts, History).
- Server: `/restaurants/{r}/inventory/*` (items, kinds, locations, movements, counts, activity, item events, photos).

## How it is now
- Items and places have kinds the restaurant manages; places can have their own picture.
- Stock changes (receive, waste, transfer, returns, adjustments) need an `Idempotency-Key`.
- Deleting an item or a place writes off or moves its stock; counts block deleting.
- Item changes are recorded (History → Items).

## Known gaps
- Places, kinds and counts write no history events yet.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
