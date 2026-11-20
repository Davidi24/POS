# 20. Statistics

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Statistics workspace (Overview, Sales, Finance, Orders, Dishes, Stock, Tables, Team), Sales details, downloads.
- Server: `/statistics/*` (REPORTS_READ); sales lists every dish sold (top items 500, since 2026-11-16).

## Known gaps (MOCK, worked out on the device)
- Busy hours, payments per day and refunds per method, dish costs and margins, dish sales per order type.
- Wages and labour cost, VAT (20%), card fees, money out (wages, stock, fees) on Finance.
- All of Tables (bookings) and Stock.
- Narrower filters scale the figures instead of the server filtering.
- Service hours (breakfast / lunch / dinner) should become a setting; bill bands; sales per place.
- Closed days guessed from sales (needs opening hours).
- Sales details' orders are by when they opened and need ORDER_READ (wanted: by when paid, under REPORTS_READ).
- Branch filter is switched off (`OneBranchPreview = true`, temporary); the server answers at most 366 days.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
