# 15. Orders

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: POS → Orders (list, details, items window, add item, send to kitchen, close as unpaid), POS → History (reopen).
- Server: orders, items, kitchen sync, bill request, close-unpaid, reopen-for-payment.

## How it is now
- Order status words: Draft, Active, Closed, Cancelled, Voided; food status: Not sent, In preparation, Ready, Partly served, Served.
- Reopening only from History.

## Known gaps
- The order form offers Takeaway even when Takeaway is switched off.

## Decisions to take
- Must a waiter be clocked in to open an order (with Shifts)?

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## DGII printing from an order (to do; user, 2026-11-23)
- The order's Print button offers both receipts: "Preliminary bill" (any time, again and again, order stays open) and "Fiscal
  invoice" (once; then Reprint). After a fiscal invoice the order can't be edited: changes need a credit note. Details and rules in
  `16-payments-and-receipts.md` → "DGII: preliminary bill and fiscal invoice".

## Done
- Nothing yet.
