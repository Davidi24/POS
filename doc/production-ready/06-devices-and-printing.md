# 6. Devices & printing

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Devices (kind cards, kind page, device page, add and confirm), Settings → Devices & printing.
- Server: devices, pairing tokens, heartbeats, printers (`RestaurantDeviceRetirement` for archiving).

## How it is now
- Kinds: Tills (`POS_TERMINAL`), Tablets & screens, Printers, Card terminals, Barcode scanners, Cash drawers.
- Adding: pick the kind, editor, then Confirm (pairing code for tills, tablets and terminals).

## Known gaps (MOCK)
- [ ] Barcode scanners and cash drawers live only in memory (`DeviceExtras.kt`); the server has no SCANNER / CASH_DRAWER types.
- [ ] The printer test page and "Open the drawer" do nothing.
- [ ] Nothing prints yet: receipts, kitchen tickets (KDS print button shows a toast).

## 1. Screens
- [ ] Agree the screens with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- [ ] The feature complete, after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- [ ] Written once development is done.
- [ ] Run (later, when the user says).

## Printing the two receipts (to do; user, 2026-11-23)
- The receipt printers print what Restaurant info → Receipt designs: the preliminary bill and the fiscal invoice (blocks, lines,
  pictures, barcode, QR codes, DGII's QR). The design is on the device for now and needs the server first (decide-later step 4 item 9).

## Done
- Nothing yet.
