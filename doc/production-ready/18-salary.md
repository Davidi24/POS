# 18. Salary

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Salary (team cards, team page, pay dialogs, pay setup).
- Server: `staff_pay_rates` (hourly or monthly), `PUT /shifts/pay-rates/{user}`, `/shifts/pay`, role pay settings.

## How it is now
- Pay = worked minutes × the shift's wage + tips on orders the person created; or a monthly salary.

## Known gaps (MOCK, in memory: `SalaryPlans`)
- Moving a person to another team, and payouts (Pay / Mark as not paid). Marking paid sends no money.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
