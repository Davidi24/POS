# 18. My Sales

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: POS → My Sales.
- Server: my sales (`MySalesResponse`).

## How it is now
- Demo data exists for `owner` on 26–31 Oct.

## This step
- Check every figure against real orders, payments and shifts.

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: My Sales works on PIN.
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
