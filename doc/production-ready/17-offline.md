# 17. Offline

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

Why here (2026-11-17): it's about the till, the tablets and the kitchen screen keeping service going when the internet drops, so it
comes once Kitchen, Tables, Orders and Payments (steps 12–16) work properly online. Those steps keep it in mind (see "Before this step").

## Where it is
- App: every workspace; mostly POS (Tables, Orders, Take payment), the kitchen screen (KDS) and printing.
- Server: order writes, payments and stock movements (`OrderWriteFilter`), the live updates (`OrderChangeNotifier`, `TableLayoutChangeNotifier`).

## How it is now
- Nothing works without the server: every screen loads from it and every change is sent at once.
- The orders module says so itself: "there is no offline write queue".
- A good base already exists: payments and stock movements need an `Idempotency-Key`, so a change sent twice is saved once.

## Known gaps
- No copy of the menu, tables or open orders on the device; no list of changes waiting to be sent.

## Decisions to take
- What must work offline: taking orders, sending them to the kitchen, cash payments, printing receipts (card payments need the internet).
- Which devices: tills, tablets, the kitchen screen, phones.
- How the kitchen gets orders without the internet: through the restaurant's own Wi-Fi from a till, or only once the internet is back.
- What happens when two devices changed the same order or table while offline.
- Order numbers created offline, and how long the restaurant may stay offline.
- Signing in while offline (with a PIN on a paired device, from step 1).

## Before this step (steps 12–16)
- New orders, dishes and payments get their id on the device and an idempotency key, so they can be sent later without duplicates.

## 1. Screens
- An "Offline" sign in the top bar with how many changes wait to be sent; what each screen shows and allows while offline.

## 2. Development (the feature complete, no tests)
- A copy on the device of what service needs (menu, tables, open orders, staff), kept up to date.
- A queue of changes made offline, sent in order when the connection is back; the rules for clashes.
- The server side for sending a batch of changes.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
