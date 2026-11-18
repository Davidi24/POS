# 6. Devices & printing

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Devices (kind cards, kind page, device page, add and confirm), Settings → Devices & printing.
- Server: devices, pairing tokens, heartbeats, printers (`RestaurantDeviceRetirement` for archiving).

## How it is now
- Kinds: Tills (`POS_TERMINAL`), Tablets & screens, Printers, Card terminals, Barcode scanners, Cash drawers.
- Adding: pick the kind, editor, then Confirm (pairing code for tills, tablets and terminals).

## Known gaps (MOCK)
- Barcode scanners and cash drawers live only in memory (`DeviceExtras.kt`); the server has no SCANNER / CASH_DRAWER types.
- The printer test page and "Open the drawer" do nothing.
- Nothing prints yet: receipts, kitchen tickets (KDS print button shows a toast).

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
