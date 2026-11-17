# 15. Payments & receipts

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Collect payment (`TakePaymentScreen`, split, pay by dishes), receipts, Settings → Payments & receipts.
- Server: `/restaurants/{r}/orders/{o}/payments`, receipt, refunds, cancel, `PaymentCalculator`.

## How it is now
- Payments need an `Idempotency-Key`; paying by dishes stores `payment_lines`.
- Payment provider is `test` (`app.payments.provider=test`).

## Known gaps
- No real card provider connected.
- Receipts aren't printed or emailed.
- Booking deposits counted towards the bill: check here.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
