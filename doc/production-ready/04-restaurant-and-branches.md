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
- [ ] Opening hours and the restaurant's own page (Admin Hub → Restaurant info): looked at together after the merge (2026-11-22).
  - [x] First round (user, 2026-11-22: "the pages are so bad, make them look much better; every branch can have them different, so
    you go inside a branch to do these; legal and currency at the end of the bar, we need to discuss them, dangerous to change"):
    the whole page redone (cover picture on top, sections with pictures); Branches are cards that open each branch's own page
    (its details, address, phone, email and opening hours); Address & contacts became Contacts (the company's); Legal & tax and
    Currency & time zone sit apart at the end with an amber "Careful".
  - [x] Second round (user, 2026-11-23: "remove legal, everything for taxation goes to Finance; a user who comes here is inside
    a branch, so leave only things like opening hours, logo"): no Legal & tax (legal name and tax numbers moved to Finance &
    taxation → Tax setup); inside a branch the tabs are Details, Opening hours (that branch), Contacts (this branch + the
    company), Logo & receipts (+ Currency & time zone for the Super Admin); the Branches list stays only in the Super Admin's
    Restaurant app, outside any branch.
  - [x] Third round (user, 2026-11-23: "merge details and contacts; the green in Open now I don't like"): one Details page (the
    restaurant, this branch, the restaurant as a company, one Save); "open" in the app's own green.
  - [x] Fourth round (user, 2026-11-23: "this page looks terrible, and it repeats how guests reach you: just contact; it can
    add things like the Instagram link"; the list agreed first): Details is one form, each thing once: Who you are (name,
    legal name; the code only for the Super Admin), About, Contact (phone, email, website, the address of the branch you're
    in), Social links (Instagram, Facebook, TikTok, WhatsApp, Google Maps; kept on the device until the server has them),
    beside how guests see it; the Super Admin's owner, open or closed and remove at the end. The company's addresses moved to
    Finance & taxation → Tax setup; "People to contact" is no longer shown.
    Then: no booking-website preview (the website isn't built yet), the four parts as cards two a row, and an optional exact
    location (latitude, longitude) for the address.
    Then: an Edit mode (the details show as text until Edit), Who you are (with the description) and Social links on the left,
    Contact and a Map (a pin at the exact location; the real map comes later) on the right, social links checked, and one phone
    and email for the restaurant and the branch (changed in either place, both change).
    Then: Edit on every card (one at a time, Cancel / Save in the card), a real map (OpenStreetMap) where a click in Edit puts
    the pin, and every social network shown with "Not set" when empty.
  - [x] Opening hours as a calendar (user, 2026-11-23): a month calendar, each day with its hours; click a day to close it,
    give it other hours or put it back to the usual hours (several days in its window); the usual week as text with Change.
  - [ ] Currency & time zone: talk through how it should work (who changes it, what's asked first).
  - [ ] The user looks at the new pages.

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
27. Restaurant info → Details: each card shows what's saved ("Not set" when empty) and changes only after its Edit; one card at a
    time (the others' Edit fades); Cancel puts it back, Save saves; owners can't change the name, legal name or code (locked)
    and never see the code; the Super Admin can.
28. Social links: Instagram / TikTok accept their link or an @name, Facebook its link, WhatsApp a number or wa.me, Google Maps a
    share link; anything else is refused with the message under the field (app) and 400 from `PUT /restaurants/{r}` (spaces,
    over 200 characters); saved ones come back after a restart; "" takes one off; the restaurant list's quick edit leaves them.
29. Contact: the phone and email are the restaurant's and the branch's together: changed in Details, the branch page shows them;
    changed on the branch page (Super Admin), Details shows them.
30. Map: the real map shows the pin; in Edit a click puts the pin there and fills the exact location; typed coordinates move the
    map; wrong numbers get a message; coordinates without a street are refused ("Add the address first"); they're saved with
    the branch's address (latitude/longitude, 400 out of range or one without the other) and come back after a restart; "Open it
    in Google Maps" opens the spot; the map's credit shows.
31. Logo: the camera button on the top card picks a picture, the placing window zooms (100–400 %) and drags it, Use this logo
    saves the square as a PNG; Remove the logo; the restaurant's logo replaces the app's in every top bar (Admin Hub, POS desktop
    and phone, the workspace picker) for every role, plain with no tile; without a logo the app's own shows.
32. Banner: the owner changes and removes the restaurant's cover ("Change cover"); the owner can't change a branch's cover (403);
    the Super Admin can.
33. Opening hours as a calendar: the month shows each day's hours, closed days and special days (red closed, blue other hours)
    with their notes; past days can't be clicked; a day opens its window (Usual hours / Closed / Other hours, the range calendar
    for several days, openings, a note); Usual hours on a special day removes it; the usual week changes in its own window and
    saves; overlapping special days are refused (409); bookings that fall in closed time open the window that lists them and
    Go to Reservations opens Reservations.
34. Receipt (Restaurant info → Receipt): the Preliminary bill and the Fiscal invoice are designed on their own; the preliminary
    bill always has "PRECUENTA – NO VÁLIDA PARA FINES FISCALES" and never the fiscal blocks or Payment; the fiscal invoice
    always has the e-NCF details and DGII's QR code (E32 / E31 switch, test numbers, the amber note); required blocks can be
    moved, not removed or copied.
35. Receipt editing: add text, line, picture, barcode, QR code, space and pictures behind; built-in blocks; click picks, drag
    moves up and down (also past hidden ones), pictures behind drag anywhere; Layers picks, moves and hides; right-click menus
    (Copy, Paste below, Duplicate, Move, Hide, Remove) on the paper and in Layers, Paste on the board, also onto the other
    receipt; Ctrl+C / Ctrl+V / Ctrl+D / Delete once something is picked; the wheel moves the paper and Fit shows it whole.
36. Receipt words and codes: built-in blocks' own words (restaurant lines, order words, subtotal / VAT / total, change, dish
    heading, a line under the precuenta notice) print as typed and "Use the restaurant's details again" puts them back; the QR
    code's link is always editable and the chips fill it in from Details; the barcode holds the receipt number or your own
    code (accents refused); both scan.
37. Receipt saving: nothing applies until Save; the bar saves the texts (branding) and the design (`PUT …/receipt-design`)
    together; leaving with changes (another tab, another Admin Hub place, the back arrow) asks Save / Discard / stay; a design
    saved from another device meanwhile gives 409 and the message; a design that's not an object or over 256 KB is a 400; it
    comes back the same after a restart; reading it needs RESTAURANTS_READ, saving RESTAURANTS_UPDATE and the restaurant.
38. Receipt pictures: uploaded at once (PNG/JPEG/WebP ≤ 5 MB); a picture taken off and saved is deleted (unless something else
    uses it); a picture uploaded but never saved is deleted after a day; a saved one stays.
39. Colours: Logo & receipts has no colours and the branding API neither answers nor takes them.
40. Finance & taxation (screens, test data) and choosing the branch to work in: they open and look right; every branch
    together shows "Coming soon".
## Done
- Nothing yet.
