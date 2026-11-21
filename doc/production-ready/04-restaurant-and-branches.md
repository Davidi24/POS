# 4. Restaurant & branches (plus opening hours)

Status: Decisions (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Restaurant info; Super Admin: restaurant list, branch list, a restaurant's page (Details, Branches, Places, Branding, Legal, Money).
- Server: `/restaurants`, `/restaurants/{r}/branches`, currency and time zone preview/change, owner add/invite/transfer, logo and covers, branch removal check, `/branches/{b}/business-hours`, `/special-hours/calendar`.

## How it is now
- Finished on 2026-10-24 (the user's checklist was built and tested): required phone and email, ownership transfer, archiving retires devices, public applications with review, reserved codes, paged branch chooser.
- The owner changes contact details, addresses, logo, colours and receipt texts; only super admins change name, legal name, code, status, tax profiles and branches.

## Known gaps
- [ ] Opening hours and special days exist on the server but the app doesn't show or use them (Statistics guesses closed days from the sales).

## To do in this step
- [ ] A quick re-check of what was finished.
- [x] Opening hours and special days: where they're set, and used by Statistics (and later by bookings). (First version built.)
- [x] Today's exchange rate filled in when a restaurant's currency is changed (decided 2026-11-20: yes, it can be changed).

## Decisions (user, 2026-11-20)
- [x] Re-check of the finished pages: together, on screen, now (before the new screens).
- [x] Opening hours are set and shown in Admin Hub → Restaurant info, per branch; each branch has its own.
- [x] A day can have any number of openings: each one when it opens and when it closes (lunch and dinner, a break,
  past midnight), or closed.
- [x] Special days: a date or a range of dates (closed 1–7 Jan), closed or other hours, with a note.
- [x] Who changes them: a new permission ("Change opening hours"): the owner has it, and by default Admin and Manager
  too; the owner can take it away from them.
- [x] While a branch is closed the POS is blocked: nobody seats guests or starts orders (open orders can still be paid).
  A new permission "Open outside the hours" (default Manager, the owner can take it away) opens the branch for now
  (a late group, an event).
- [x] Opening hours are used by Statistics (closed days from the hours, not guessed from the sales), online booking
  (guests book only when open), staff bookings (a time outside the hours asks "The branch is closed then, book
  anyway?") and shown to guests (booking page and booking emails).
- [x] Currency change: the "1 old = ? new" box starts with today's rate (the app already loads rates), and can be
  changed. Past orders keep their currency and each change is saved with its day and rate (already so).

## Built (2026-11-20, first version, to look at together)
- [x] Demo pictures: every restaurant and branch has a cover picture and a logo (local database, through the API).
- [x] Server: V131 (`branch_opening_hours`, `branch_special_days` + `branch_special_day_hours`, `branch_open_overrides`,
  `branches.opening_hours_set`; the old business/special hours copied in), `pos.pos.hours` (`OpeningHoursService`,
  `/restaurants/{r}/branches/{b}/opening-hours`: GET, /now, /at?time=, /calendar, PUT /week, special days POST/PUT/DELETE,
  open-now POST/DELETE). Permissions HOURS_UPDATE "Change opening hours" (Admin Hub → Restaurant info; Admin, Manager) and
  OPEN_OUTSIDE_HOURS "Open outside the hours" (POS → Tables; Manager). A branch whose hours were never set counts as
  always open.
- [x] POS blocked while closed: starting an order, seating at a table, seating a booking or the waitlist answer 409 "The
  branch is closed now…"; paying open orders still works. A strip under the POS bar (Tables, Orders, Reservations) says
  "Closed now · Opens at 20:00" with Open now (for an hour, 2, 3 or until a time, at most 18 h), and "Open outside the
  hours until 21:30" with Close again.
- [x] Admin Hub → Restaurant info → Opening hours (visible with "Change opening hours" too; a manager sees only this tab):
  branch chips, an open/closed-now card, the week (a switch per day, any number of openings, "next day" for past
  midnight, copy to every day / Mon–Fri / the weekend), special days (date or range on a calendar, closed or other
  hours, a note), saved with the floating bar / their own dialog.
- [x] Online booking refuses a time when the branch is closed; the guest booking page and booking emails show the week
  ("Opening hours: Mon–Thu 11:00–23:30 · …", in the guest's language).
- [x] Staff bookings: a time outside the hours shows "The branch is closed then. You can still book it." and asks "Book
  anyway?" before booking.
- [x] Statistics: closed days come from the hours (a day every branch with hours is closed), guessed from the sales only
  while no branch has hours.
- [x] Currency change: the rate starts with today's rate ("Today's rate · date. You can change it.").
- Demo: Main Branch Mon–Thu 11:00–23:30, Fri–Sat 11:00–01:00, Sun 11:00–23:00, special days 24 Dec (11:00–16:00), 25–26
  Dec closed, 31 Dec 19:00–02:00. Durrës Seafront closed Mondays, lunch and dinner, Christmas 24–26 Dec closed. Blloku has
  none (to see the "not set yet" look).

## 1. Screens
- [ ] Agree the screens with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- [ ] The feature complete, after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- [ ] Written once development is done.
- [ ] Run (later, when the user says).

## Done
- Nothing yet.
