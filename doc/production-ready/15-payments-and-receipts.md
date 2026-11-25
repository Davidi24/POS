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

## DGII: preliminary bill and fiscal invoice (to do here; user, 2026-11-23)
The two receipts are already designed in Restaurant info → Receipt (preview of each, DGII faked). When this step is worked on,
wire them into the sale:
- **Preliminary bill (precuenta):** printed any time before the sale is finished, as often as wanted. No NCF/e-NCF, nothing sent
  to DGII. Shows the dishes, quantities, prices, taxes and total, and "PRECUENTA – NO VÁLIDA PARA FINES FISCALES". The order can
  still be changed after it.
- **Fiscal invoice (factura fiscal):** issues the e-CF through DGII (E32 for a final consumer, E31 for a business that gives its
  RNC and name), with all the fiscal details and DGII's verification QR code. The guest can get it before or after paying.
  Recording the payment never makes a second fiscal invoice; reprinting prints the same invoice (same e-NCF, marked as a copy).
- **Rules:** printing a preliminary bill never finishes the sale; every finished taxable sale has its fiscal document (if none was
  issued, paying issues it); once a fiscal invoice is issued the order is locked, and changes go through DGII's correction (a credit
  note, nota de crédito, E34); split payments and several payment methods on one invoice; DGII sending, validation and errors as the
  e-CF rules say (retry, "pending at DGII" state, contingency).
- **Workflow:** order → (optional) preliminary bill → guest confirms → fiscal invoice → payment recorded → guest keeps the invoice.
- **Faked until the end:** DGII isn't connected; numbers are test ones (sequence per e-CF type, security code, signing date, the
  timbre link `ecf.dgii.gov.do/testecf/ConsultaTimbre?...`, `platform/ReceiptLook.kt` `dgiiTimbre`). Connecting DGII (certificate,
  test/production environment, sequences authorised by DGII) comes last.

## Done
- Nothing yet.
