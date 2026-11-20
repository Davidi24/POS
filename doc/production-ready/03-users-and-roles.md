# 3. Users & roles

Status: Development (branch `users-and-roles-production`, 2026-11-19) (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Users (role cards, a role's people, Permissions windows, New role, person editor, a person's own permissions).
- Server: `/users`, `/users/{id}/permissions`, `/roles`, `/roles/{id}/permissions`, `/roles/{id}/status`, `/roles/{id}/pay`, `/co-super-admins/{userId}/permissions`, `/auth/register`.

## How it is now
- Who sees and manages whom: people ranked below you, plus people at your level you added yourself; never above you (`RoleHierarchyService`, `assertCanManageUser`).
- Built-in roles from high to low: Super Admin 60000, Co-Super-Admin 55000, Owner 50000, Co-Owner 40000, Admin 30000, Manager 20000, Waiter 10000, Viewer 7500, Kitchen 5000, Cleaner 4000, Extra worker 3000. A restaurant's own role gets the rank just under its maker.
- Built-in roles are shared; a restaurant's changes to them are stored in `restaurant_role_permissions`. A person's own changes are grants and denials.
- Roles can be switched off per restaurant and marked paid or not (`role_pay_settings`).
- Nothing about the Super Admin shows inside a restaurant.
- Staff search forgives accents, typos and partial names (`StaffSearch`).

## Known gaps
- [x] The Kitchen role wasn't assignable, so an owner couldn't add kitchen staff (403). Assignable since 2026-11-19.
- Role seeding only adds permissions; taking one off a built-in role needs a migration (how it works, not a to-do).

## Decisions (user, 2026-11-19)
- [x] **Kitchen staff:** anyone ranked above Kitchen adds them (owner, co-owner, admin, manager), like waiters. The Kitchen role
  becomes assignable.
- [x] **Removing someone:** signed out of every device at once; their planned shifts that haven't started are removed; worked
  shifts, pay, orders and history keep their name, marked "removed"; open orders they started stay open for the team.
- [x] **Permissions when adding someone** (user: "when creating a user it says fill its permissions, by default they're on, only if
  you want to add or remove"): the add-person form has a Permissions part. Picking the role(s) shows what they give, all on;
  the person adding can switch single ones off or add others (e.g. a new manager who may also add people) before saving.
  Saved as that person's own changes (grants and denials), as the Permissions window does today. Nobody gives a permission
  they don't have themselves (already the server's rule).
- [x] **Admin** (user, 2026-11-19, second round): opens Admin Hub (shifts, users, settings without payment and fraud, audit
  log), Inventory (stock and suppliers) and Statistics. Salary, devices, restaurant info, roles, sign-in sessions and Fraud
  Detection are the owner's; the owner can give any of them, with a "Be careful" warning. POS and the kitchen display come
  from the roles below (Manager, Waiter, Kitchen…), all switched on by default when an admin is added; whoever adds them
  can switch them off. Existing admins got those roles (V127).
- [x] **Adding a person:** one main role, then "Other roles" unfolds the roles below it.
- [x] **Owners:** no staff before a restaurant has an owner. An owner can add another owner (warning: the same
  permissions). Owners remove co-owners, never another owner: they ask the Super Admin, who checks with that owner and
  approves or declines.
- [x] **Admins of two kinds, all called Admin:** support admins (the old Co-Super-Admins) are made by the Super Admin and
  take care of the restaurants given to them, one, several or all ("All restaurants"); they remove owners and co-owners
  only when the Super Admin allows it. Restaurant admins are made by the owner and work only there. The Super Admin's
  Admins page shows both. Owners see their support admins in Users → Admin and can write to them, or ask for one.
- [x] **Pay follows the highest role:** an admin who also has Waiter isn't paid as a waiter.
- [x] **Role change later:** the person's own changes are cleared; the editor shows the new role's permissions, all on, and the
  changes are made again before saving.

## 1. Screens
Built 2026-11-19; the user's look: "good" (2026-11-19).
- [x] Add a person in two parts in one window ("1 Details · 2 Permissions"): details and roles, then the roles' permissions as
    the workspace cards, all on, with "2 more than Manager · 1 taken away" and "Same as the role". Back / Add person.
    Saved through the existing `PUT /users/{id}/permissions` right after the person is made (only when something changed).
- [x] Edit a person: changing their roles shows an amber note and goes through the same Permissions part (Next: permissions),
    starting from the new roles; saving sends what's set there, which clears their old own changes.
- [x] Removing a person: the question lists what happens (signed out everywhere, unstarted shifts removed, history stays marked
    removed, open orders stay open). MOCK until development: the server doesn't do the sign-out/shift part yet.
- [x] Kitchen in the roles owners and managers can give (`AppRole.KITCHEN` assignable; startup updates the flag).

## 2. Development (the feature complete, no tests)
Started 2026-11-19 (user: screens "good", then "make the permissions have a meaning… make POS completely at top, then go
down… start with the development"):
- [x] Permission tree on the server (`PermissionTree`, `parentCode` on `/permissions`; saving drops what's missing its parent;
    V126 takes the Kitchen display's View/Update off the Manager, who couldn't open it). App: switching follows the tree,
    cards one under another with POS first, each card's switch its "Open …", parts with their "View …" first and the rest
    indented under it.
- [x] Removing someone: signed out everywhere (USER_NOT_ALLOWED), planned shifts not started deleted, audit line, "(removed)"
    after their name on shifts and payments.
- [x] A role change clears the person's own grants and denials on the server too.
- [x] Permissions: Suppliers have their own (View / Change Suppliers), and Salary, Devices and payment/fraud settings too
  (SALARY_MANAGE, DEVICES_MANAGE, SETTINGS_MONEY); Admin Hub parts, Settings categories and the Suppliers tab follow them.
  Owner-level permissions carry `careful` and ask "Be careful" before they're given. Cards: big ones full width, small
  ones (Inventory, Kitchen display…) side by side, each part a soft block with "View …" first.
- [x] Support admins (V127: `users.admin_all_restaurants`, `admin_may_remove_owners`, `admin_restaurant_access`), the
  Super Admin's Admins page (Support admins | Restaurant admins, Restaurants dialog) and Requests page
  (`platform_requests`: remove an owner, need a support admin), Users → Admin's support admins with Email them.
- [x] Every permission and role name translated in the four languages.
- [ ] The user looks at the new permission cards (one under another, POS first, "View" before editing) and tries removing a person.
- [x] Permissions by place (user, 2026-11-19: "a card for each, Tables, Orders, Menu…; the kitchen sees maybe only the
  tickets or Upcoming; Inventory's stock and items with their own, and I choose if the chef can receive, waste…; why isn't
  the audit log there; Statistics maybe only Sales, not Finance; all nav bars with chosen"): every place of every bar has
  its own "See …" (`PermissionTree` parts, sent as `workspace`/`part`/`position` on `/permissions`), what changes things has
  its own (Seat guests and free tables, Edit the floor plan, Receive, Waste, Move, Correct, Manage places, Add/Change/Delete
  items, Count, Approve counts), My Sales and History their own, My shift its own card shown in the POS and on the kitchen
  display (Kitchen clocks in there), the Audit log its own place, Statistics one per page (Admin without Finance). Plainer
  names; the Super Admin's own hidden in restaurants. V128 gives the new ones to whoever had what they came from and
  removes "Change Stock". Every bar, the workspace picker and the table panel follow them; the server refuses each change.
  Each place is a card with its "See …" switch in the role and person windows. Kitchen by default: the kitchen display,
  My shift, and Inventory's stock and items to look at.
- [x] The user's final confirmation of [permissions.md](permissions.md) (generated from the code): POS confirmed one place at
  a time on 2026-11-20; the other workspaces later (to do below).
- [x] Full backend suite: the 35 tests the new rules broke updated (role tests act as an admin who may manage roles via
  grants, settings/device tests as a co-owner, pay as a co-owner, Kitchen assignable, removal reason USER_NOT_ALLOWED).
- [ ] Full backend suite again after the permissions by place, then commit, push and PR to `develop` (after the final
  confirmation).

## Final decisions on the permissions (user, 2026-11-20, one place at a time; built after the last one)
- [x] POS → Tables: four permissions. (1) Edit the floor plan: tables and floors, one switch. (2) Take payments: Request
  bill and Pay on a table. (3) Change tables: seat, set free, unavailable, still here, move, join (without it they only
  look). (4) Which floors: the floors they can see, not all by default. Everything else on a table comes from the other
  places' permissions; its reservation buttons (seat, see, change table) show only to people with Reservations access.
- [x] POS → Orders: access (see them, start, cancel, print and the rest), then (1) Edit orders, (2) Take payments,
  (3) Close as unpaid (its own, apart from void), (4) Void what was sent, (5) Move an order to another table, (6) Give
  discounts, (7) Kitchen Status.
- [x] POS → Reservations: access only looks (calendar, list, overview, walk-in tables, links); every change needs one of
  (1) Make and change bookings (new, time, guests, notes, table, confirm attendance, couldn't reach, payment link, check
  in, seat, undo seating), (2) Cancel bookings (cancel, no-show), (3) Approve bookings, (4) Fix past bookings, (5) Give
  goodwill refunds (be careful). Food ordered ahead follows Orders' Edit orders; Booking areas follows Edit the floor plan.
- [x] POS → Menu: access only looks; then (1) Mark sold out (its own, for waiters or the kitchen), (2) Add dishes and
  menus, (3) Change dishes and menus (photos, ingredients, order, what waiters see; not prices), (4) Change prices (its
  own, be careful), (5) Delete dishes and menus.
- [x] My shift: access = their own shifts, clock in/out, breaks; then (1) See their pay (hours, wage, tips), (2) See
  others' shifts (the Team tab) with a choice of whose roles they see (replaces the setting "Staff see the whole team").
- [x] My Sales: one permission, their own sales and tips only; nobody sees someone else's there (the pick-a-person
  option goes).
- [x] POS → History: access = the list, search, an order's bill, printing again; then (1) Reopen for payment, (2) Reopen
  orders (be careful), (3) Refund payments (be careful), (4) See what changed on orders.
- [x] Switching a place or workspace off and on again brings back what was on under it (user, 2026-11-20: "it remembers
  the permissions"), in the role, person and Add person windows.
- [x] POS built 2026-11-20 (V129: Change tables replaces Seat guests and takes joining from Move orders; Take payments on
  a table; Close as unpaid, Cancel bookings, Mark sold out, Change prices, See their pay, See others' shifts, Reopen for
  payment; starting and cancelling orders come with Edit orders; `permission_scopes` for floors and roles; the setting
  "Staff see the whole team" became the permission). Starting and cancelling an order are in Edit orders, not in
  access, so a Viewer stays read-only.
- [x] Round two on the person page (user, 2026-11-20): "See tables" chooses where as a tree, branches (with more than
  one) → floors → places, ticked inline under the permission with a clear Choose button ("hard to understand that you can
  edit it"); "See others' shifts" the same with roles. Saving keeps the page (and the role window) open and plays "Permissions
  saved"; the header shows the person, their role and how far from it they are. Leaving either with changes not saved asks
  "Save your changes?" Discard / Save.
- [x] Kitchen Status is two (user, 2026-11-20): "See an order's kitchen status" in Orders (food status and what's not sent,
  on orders and on a table) and "See Kitchen Status", the whole board, as its own place (V130).
- [x] Kitchen display, Inventory, Admin Hub, Statistics, Fraud: kept as they are for now (user, 2026-11-20: "let's say
  they are good now, I'll check them later").
- [ ] To do later: decide the kitchen display, Inventory, Admin Hub, Statistics and Fraud permissions one at a time, then
  check the permissions again for every workspace (user, 2026-11-20). Suggestions waiting for the kitchen display: Send
  tickets back and Set up the kitchen as their own, and its own Mark sold out.

## 3. Hard testing list (run later, when the user says)
- [x] Written (2026-11-19).
- [ ] Run (later, when the user says):

1. Add a person as owner, co-owner, admin and manager: the roles offered are the ones below you (and your own level for
   people you add), Kitchen included; Next: permissions shows exactly what the role gives, all on.
2. In the Permissions part switch a few off and add one you have: the person signs in with exactly that (check the menus and
   an endpoint they shouldn't reach answers 403). Adding one you don't have is locked.
3. Add a person without touching the Permissions part: nothing is stored for them alone (Permissions window says "Same as
   the role").
4. Permission tree in all three places (a role's Permissions window, a person's Permissions page, Add a person): switching off
   a workspace's Open closes the card and takes everything inside off; switching on an edit also switches on its View and the
   Open; a part's View off fades and switches off what's under it; "Needs “View Orders”" on Tables and History.
5. The same through the API: PUT /roles/{id}/permissions and /users/{id}/permissions with an edit but no view keep only what
   has its parents; built-in roles in a restaurant store only the differences.
6. The Manager no longer shows Kitchen display permissions; managers can still use POS → Kitchen Status (it reads with
   ORDER_READ) and nothing else they used breaks.
7. Edit a person and change their role: the amber note, the Permissions part from the new role; after saving, their old
   extras and denials are gone (also when the role is changed by the API directly).
8. A Co-Super-Admin's role list replaced by the Super Admin keeps their per-person limits.
9. Remove a person who is signed in on two devices: both show "Account switched off" at their next request; their planned
   shifts after now are gone, today's worked and past shifts stay with "(removed)" after the name in Shifts, Salary and
   payment lists; an order they opened stays open and can be paid by others; the Audit log has the line.
10. A removed person can't sign in (wrong-credentials answer, no hint the account existed) and doesn't come back in staff
    lists or as a choice anywhere.
11. Shift plans don't make new shifts for a removed person at the next hourly top-up.
12. Permissions by place: a waiter without "See tables" has no Tables in the POS bar and the POS opens on Orders; without
    "See history" no History in the bar, on Orders or on a table's panel; without "Seat guests and free tables" no Seat,
    Set free, Change guest count or Mark unavailable (and PATCH …/tables/{t}/status answers 403); without "Move and join
    tables" no Join, Unmerge or Move guests; without "Edit the floor plan" no Edit.
13. Kitchen: the kitchen display with My shift (clock in, break, out), Inventory with only Stock and Items, no change buttons;
    give them Receive and Waste only: those two show and work, Move/Correct/Manage places/New item stay hidden and the
    server answers 403 for them.
14. Inventory: Approve counts off: a count can be finished but not approved (403 too); Delete items off: no Delete.
15. Statistics: only Sales on: the workspace opens on Sales and no other page is in the bar; Finance off for an admin.
16. Kitchen display: only Tickets, or only Upcoming: the bar shows just that and it loads.
17. Admin Hub: the Audit log is its own card; off: no Audit log in the bar.
18. After the update (V128): a restaurant's own role that had "View Stock"/"Change Stock" has every inventory place and
    change; one that had "View Orders" has Tables, Kitchen Status and History; nothing anyone could do is lost.
19. POS decisions (2026-11-20): a role with "See tables" for one floor sees only that floor (the others aren't in the
    floor switch, and the layout API leaves them out); renaming the floor keeps it; "See others' shifts" for Kitchen
    only shows only kitchen people in My shift → Team; without "See their pay" no pay card and `/shifts/pay?mine` is 403;
    without "Change prices" a saved dish's price is locked and a PUT with another price is 403, a new dish still gets its
    price; Mark sold out alone flips Available only; Cancel bookings off hides Cancel / No-show / Left before seated (403
    too); Close as unpaid apart from Void; Take payments on a table without Orders' payments can request the bill and pay;
    My Sales shows only their own (staffId of someone else 403); switching Orders off and on brings back what was on;
    "Where" for one place shows only that place's tables (and its floor), a whole branch every floor of it; saving the
    person page or role window keeps it open with "Permissions saved"; without "See an order's kitchen status" no food
    status on orders, the Status filter or a table's panel.
20. Everything above in the four languages, on a phone and a desktop.

## Done
- Nothing yet.
