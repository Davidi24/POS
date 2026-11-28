# 24. Devices & printing, second look

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

Added 2026-11-28 (user: "I still want to recheck these devices, so put it again after the 23 one, before the web"). Step 5
built Devices & printing on real server data and its pull request went to `develop`; this step is the user's second look at
the same screens once the rest of the app (orders, payments, kitchen, offline, finance) is finished, so the devices are
checked with everything that uses them.

Branch: `devices-second-look-production`, from `develop`.

## What to look at again
- Admin Hub → Devices: the six kind cards, each kind's page, a device's page (Connection, Who can sign in, State, Details).
- Adding a printer, a card terminal or a cash drawer: Search for it (Network | Cable | Bluetooth) or Connect it by hand, then
  Name it; Bluetooth: Allow, Turn on, look nearby, Pair.
- Connecting a till or tablet: Use this device, or a code made on the till's page and typed inside Tills / Tablets & screens.
- Printers: what each prints (tills' receipts, kitchen stations' tickets), Online / Offline, the test page, document printers.
- Card terminals through their till or tablet (Bluetooth or cable), cash drawers through their printer, scanning anywhere.
- Settings → Devices & printing: the restaurant's network, Till lock, When things print.
- Printing for real from Orders and Payments (step 14 and 15): kitchen tickets on send, the bill, the receipt after payment,
  the receipt designer's two receipts (preliminary bill and fiscal invoice), the drawer opening on cash.
- The kitchen station's ticket look (letter size, notes in red).

## Still waiting on outside things (from step 5)
- A card payment provider: until then a card terminal asks the person how the card went (test mode).
- DGII: the fiscal invoice prints test numbers until it's connected.

## Decisions to take
- [ ] Whatever the user changes while looking again.

## 1. Screens
- [ ] Go through the screens above with the user.

## 2. Development (the changes asked, no tests)
- [ ] The changes the user asks for.

## 3. Hard testing list (run later, when the user says)
- [ ] Step 5's list ([05-devices-and-printing.md](05-devices-and-printing.md), section 3) plus anything this step adds.

## Done
