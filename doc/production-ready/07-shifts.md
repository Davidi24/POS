# 7. Shifts

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Shifts (month calendar, day window, Plan shifts), POS → My shift (pay, worked, team month).
- Server: `/shifts`, `/restaurants/{r}/shift-templates`, `/branches/{b}/shift-templates/{id}/plan`, clock in/out, `/shifts/pay`.

## How it is now
- Usual shifts are made by each restaurant (none to start), each for some roles; how a shift runs is planned per branch and makes real shifts 12 weeks ahead, topped up hourly.
- Clock-in copies the person's hourly wage onto the shift.
- "Staff see the whole team" setting decides what My shift → Team shows.

## Split
- Salary is its own step later (after Payments).

## Decisions to take (with Orders)
- Must a waiter be clocked in to open an order?
- Where people clock in (the till, their own phone, both).

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
