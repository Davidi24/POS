# 4. Restaurant & branches (plus opening hours)

Status: Hard testing list ready (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Restaurant info; Super Admin: restaurant list, branch list, a restaurant's page (Details, Branches, Places, Branding, Legal, Money).
- Server: `/restaurants`, `/restaurants/{r}/branches`, currency and time zone preview/change, owner add/invite/transfer, logo and covers, branch removal check, `/branches/{b}/business-hours`, `/special-hours/calendar`.

## How it is now
- Finished on 2026-10-24 (the user's checklist was built and tested): required phone and email, ownership transfer, archiving retires devices, public applications with review, reserved codes, paged branch chooser.
- The owner changes contact details, addresses, logo, colours and receipt texts; only super admins change name, legal name, code, status, tax profiles and branches.

## Known gaps
- [x] Opening hours and special days exist on the server but the app doesn't show or use them (Statistics guesses closed days from the sales). Done in this step.

## To do in this step
- [ ] A quick re-check of what was finished (the user looks at it later, 2026-11-21: "build now and open the PR").
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

## Decisions before the PR (user, 2026-11-21, one at a time)
- [x] 1. Guest booking page times: only times the branch is open on the chosen day; the last start leaves the usual booking
  length before closing (closes 23:30, bookings 2 h → last 21:30). A closed day says "Closed that day" and offers the next
  open day. (Today: 12:00–22:30 every day, the server refuses a closed start only after sending.)
- [x] 2. Bookings that fall in closed time after the hours change (a special day closed, a shorter day): the hours are saved
  at once (no new bookings then), then a list of those bookings opens with Change time (an open time that day or another)
  and Cancel (with a reason) for each, "Call the guests first", Later / Done. Bookings not handled stay marked "Branch
  closed then" (red) in Reservations until moved or cancelled, and the people told about bookings get a notification.
  Not blocking the save (agreed): a sudden closure must stop new bookings at once, moving a guest is a phone call first, and
  someone who may change the hours may have no booking permissions.
- [x] 3. "Open now" stays at most 18 hours, fixed (not a setting): it's a safety net so a branch opened for a late group isn't
  left open by mistake; longer openings on purpose are special days with their own hours.
- [x] 4. Requests follow-ups go to step 7 (Notifications), not this PR: the reporter sees their problem again ("My
  problems") and is told when it's solved; a support admin is told when a ticket is given to them.
- [x] 5. Before the PR: the full backend suite from a clean copy, failures fixed, the app's test code compiles; then commit and PR.
  Order after the decisions: walk through opening hours and the restaurant page together, build 1 and 2, write the hard
  testing list, run the suite, PR.

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

## Super Admin pages, gone through one by one (2026-11-21)
- [x] Loading: every Super Admin page shows shapes like its cards, then fades in (no spinners).
- [x] Applications: taller band, the applicant's own logo / cover (else a new-business sign), a click opens the whole
  application big with Reject / Approve; Filters (order, where, currency); 20 more demo applications.
- [x] Requests: anyone in a restaurant can "Report a problem" (profile menu) with a message; the Requests page is a board like
  the kitchen's: Open | Assigned | In progress | Solved, each ticket with its next step (Assign → Start → Solve / Handle).
- [x] Requests, more: drag and drop by rules, take it yourself, "Assign requests" permission, a history per ticket, a line
  between message and foot, and "New ticket" so the Super Admin opens one themselves.
- [x] Admins: plain back arrow; one Filters button (Assigned to, Status) instead of the restaurant dropdown; 7 more demo admins.
- [x] Branches: plain back arrow, the reload at the end of the "Its branches" row, Add branch as the right-edge tab (no tile).
- [x] Restaurant list: no add tile at the end (the edge tab is enough); open / closed in one Filters button.
- [ ] The rest of these pages, as the user goes through them.

## 1. Screens
- [x] The Super Admin pages gone through one by one with the user (2026-11-21, above).
- [ ] Opening hours and the restaurant's own page: the user looks at them later (2026-11-21).

## 2. Development (the feature complete, no tests)
- [x] Opening hours (server, POS closed strip, Restaurant info tab, bookings, Statistics), the currency rate, the Super Admin
  pages (applications, requests board, admins, branches, restaurant list), decisions 1–5 before the PR (2026-11-21).

## 3. Hard testing list (run later, when the user says)
- [x] Written (2026-11-21).
- [ ] Run (later, when the user says):

Opening hours
1. Restaurant info → Opening hours on each branch: Main Branch and Durrës show their week and special days; Blloku shows
   "not set yet". Change a day (two openings, one past midnight), copy to Mon–Fri, save: the floating bar, the toast, a
   reload shows the same. Overlapping openings, two past midnight or more than 6 a day are refused with a clear line.
2. Special days: a single date and a range (closed, and with its own hours, with a note); a range that overlaps another is
   refused (409); edit and remove ("goes back to the usual week").
3. A manager (HOURS_UPDATE) sees only the Opening hours tab in Restaurant info; someone without it can read but not change
   them; the owner can take HOURS_UPDATE away from Admin/Manager and they lose the tab.
4. Closed now: Tables, Orders and Reservations show "Closed now · Opens at …"; starting an order, seating a table, seating a
   booking and the waitlist answer 409; paying an open order works. Open now (1 h, 2 h, until a time; never more than 18 h)
   by someone with OPEN_OUTSIDE_HOURS, then "Open outside the hours until …" with Close again; without the permission no
   Open now button and the API answers 403.
5. A branch whose hours were never set (Blloku) is always open: no strip, nothing refused.
6. Staff booking at a closed time: "The branch is closed then. You can still book it." and "Book anyway?"; booking anyway works.
7. Statistics: closed days come from the hours (25–26 Dec, Durrës Mondays); with no branch hours they're guessed from sales.
8. The currency change box starts with today's rate ("Today's rate · date"), can be changed, and works offline (empty, typed).

Guest booking page (decision 1)
9. /public/book/{slug}/{branch}: only open times for the chosen day; the last time leaves the usual booking length before
   closing (Mon–Thu closes 23:30 → last 21:30; Fri 01:00 → last 23:00; a lunch and dinner day shows both); changing the day
   changes the times without a reload; today starts 15 minutes from now; late at night the page opens on the next open day.
10. A closed day (25 Dec, Durrës on a Monday) says "Closed that day." with "Next open: …", which picks that day; Book is off.
11. The date can't go past the online booking limit; the page in it / es / sq shows the words and day names in that language.
12. Posting a closed time or one too near closing (curl) answers the page with "We're closed at that time…" / "That's too close
    to closing time…"; Blloku (never set) offers 12:00–22:30 as before.

Bookings in closed time (decision 2)
13. With bookings on 29–30 Dec (demo: Hoxha family, Elsa Meksi, Bardhi family), add a special day closed 29–30 Dec: it saves at
    once and the list opens ("3 bookings fall when the branch is closed"), each with Change time and Cancel; Later closes it.
14. Change time: the calendar and the open times of the picked day (none on a closed day); Move to … moves it keeping its
    length, it leaves the list with a toast; a time whose table is taken answers the server's message in the window.
15. Cancel with a reason: it leaves the list; when the list is empty it says "All handled".
16. The Opening hours page shows a red line "N bookings fall when the branch is closed · See them" until they're handled.
17. Reservations marks those bookings "Branch closed then" (red) on the wide and narrow cards and in the panel, until moved or
    cancelled; other bookings keep their usual state.
18. The owners/managers told about bookings (not the person who changed the hours) get one notification "3 bookings fall when
    the branch is closed"; saving again without new ones sends nothing; changing the week, a special day or removing one
    all count.
19. Someone with HOURS_UPDATE but no booking permissions sees the list with "Ask someone who manages bookings…" and no
    buttons; Change time needs RESERVATION_MANAGE, Cancel RESERVATION_CANCEL.

Super Admin pages
20. Restaurant list: Filters (All / Open / Closed or suspended with counts) beside the search; Add restaurant only as the edge
    tab; Change position bottom-left; loading shapes, never a spinner.
21. Branches: plain back arrow, reload at the end of "Its branches", Add branch as the right-edge tab (draggable), a branch
    card enters the branch.
22. Applications: cards with the applicant's own logo/cover (else the new-business sign); a card opens big with Reject /
    Approve; Filters (order, where, currency) and pages of 12; Back to waiting for a rejected one (409 while another with the
    same owner waits); approving plays the celebration and emails the owner.
23. Requests board: Report a problem from the profile menu (message required, at most 5 open, not for platform admins); the
    four columns; drag and drop only where the rules allow (amber "You can't put it here", glides back, yellow toast);
    Assign / Start / Solve / Handle from the card and the big window; Back to open really goes to Open; the moved card's
    frame lights green; the History button and the window's timeline list every step with who, whom, when and the note.
24. New ticket (Super Admin, or a support admin with "Assign requests"): restaurant + message, lands in Open; a pending
    application can't be chosen (400).
25. "Assign requests": off for support admins by default; ticked in Admins → What they can do; with it they see every
    request and give them out; without it only theirs and 403 on assign; only the Super Admin gives a restaurant with it.
26. Admins: one Filters button (Assigned to, Status) with counts and the "Assigned to: … · N of M · Clear filters" line; cards
    with several restaurants keep their buttons whole; Edit opens the window with Restaurants / What they can do / Password
    link / Switch off-on / Remove, each that can't be undone asking first; the top Add support admin only while the dashed
    tile is out of sight.

## Done
- Nothing yet.
