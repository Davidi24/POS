# 7. Notifications

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: the bell in every top bar (`NotificationBell`), bottom-left toasts (`BottomToast`), Settings → Notifications.
- Server: notifications, and the "who is told" role lists in settings.

## How it is now
- Toasts pop up for: seating over the limit (`TABLE_SEATING_OVER_LIMIT`), special-case actions (`SPECIAL_CASE_ACTION`), orders closed as unpaid (`ORDER_CLOSED_UNPAID`).
- Who is told is a setting per topic: seating limits, unpaid orders, special cases, bookings (default Owner, Manager).

## This step
- [ ] The notification system itself: the list, read and unread, the settings respected.
- [ ] Each later step checks its own notifications.

## Decisions to take
- [ ] Phone notifications (push) or only inside the app? **(your call)**

## 1. Screens
- [ ] Agree the screens with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- [ ] The feature complete, after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- [ ] Written once development is done.
- [ ] Run (later, when the user says).

## Done
- Nothing yet.
