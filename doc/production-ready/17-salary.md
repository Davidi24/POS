# 17. Salary

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Salary (team cards, team page, pay dialogs, pay setup).
- Server: `staff_pay_rates` (hourly or monthly), `PUT /shifts/pay-rates/{user}`, `/shifts/pay`, role pay settings.

## How it is now
- Pay = worked minutes × the shift's wage + tips on orders the person created; or a monthly salary.

## Known gaps (MOCK, in memory: `SalaryPlans`)
- Moving a person to another team, and payouts (Pay / Mark as not paid). Marking paid sends no money.

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: all of Salary needs the password; seeing your own pay in My shift works on PIN.
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
