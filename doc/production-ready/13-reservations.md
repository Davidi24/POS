# 13. Reservations

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: POS → Reservations, Settings → Reservations, guest emails, guest pages `/public/book/{slug}/{code}` and `/public/bookings/{token}`.
- Server: reservations, availability, reminders job, no-show job, booking money (`BookingMoneyService`), pre-orders, waitlist (on Tables).

## How it is now
- Booking rules agreed 2026-11-02: confirm by themselves (setting), two reminders, "Couldn't reach", unconfirmed hold, review alerts.
- Deposits and paid extras run in test mode.

## Known gaps
- SMS isn't built.
- Guest emails only reach MailHog locally.

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: taking, changing, seating and cancelling bookings work on PIN; booking areas and booking rules need the password.
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
