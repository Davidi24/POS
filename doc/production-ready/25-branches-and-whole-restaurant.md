# 25. Branches and the whole restaurant

Status: Screens; working in a branch on, every branch together coming soon (Not started → Screens → Development → Hard testing list ready → Done)

**Now and after the first launch** (user, 2026-11-23: first "right now we will work as if there is one branch; after the first
launch we will update it for multiple branches", then "leave the page, just working inside the branches, not the gathered ones
like inventory: make them coming soon"). On now (`BRANCH_CHOICE_ON = true`, `core/session/BranchScope.kt`): the restaurant's
home after signing in with "Work in a branch", the branch switcher in the bars, each workspace at the chosen branch. Coming
soon (`ALL_BRANCHES_ON = false`): every branch together. "The whole restaurant" cards and the switcher's "All branches" show,
faded, with a "Coming soon" tag; a click on a card says it comes soon. True brings the built views back.

Added 2026-11-23 (user: "now we will do one hard shift, put much effort on it: now we work as if we had one branch, which is
perfect, but if we have more we need to implement this; add it first and I will check it; open a ticket for this and put it
in 05, I know we have not finished with 4 yet"). First added as step 5; moved to the end the same day.

## What the user asked

| Workspace | Branch level | Global level |
|---|---|---|
| POS | Yes — local orders and payments | Central configuration |
| KDS | Yes — each kitchen | Optional monitoring |
| Inventory | Local stock | Combined inventory overview |
| Admin Hub | Staff, devices, shifts | Users, roles, branches |
| Statistics | Individual branch | All branches combined |
| Finance & Taxation | Branch financial records | Consolidated reporting |
| Fraud Detection | Local alerts | Alerts across all branches |

## Which workspaces have "all branches" (reasoned with the user, 2026-11-23)
User: "reason every service: sales can include the 3; the kitchen display is something you go inside the branch to see; only the
ones reasonable to show for all". What's done at a place stays at the branch; what an owner looks at across the business also
has all branches:

| Workspace | All branches? | Why |
|---|---|---|
| POS | No | Orders, tables, bookings and payments happen at one place; menus, prices and settings are shared anyway |
| KDS | No | A kitchen screen is that branch's kitchen (the "every kitchen" monitor of the first round was taken out) |
| Inventory | Yes | Stock value, running low and counts across branches; moving stock between them |
| Admin Hub | Yes, partly | Users, roles, branches, settings and the audit log are the restaurant's; shifts, salary and devices stay per branch |
| Statistics | Yes | Sales of every branch combined, or one branch |
| Finance & taxation | Yes | Taxes are filed once for the restaurant (one company); a branch shows its own part |
| Fraud Detection | Yes | Suspicious activity anywhere in the business |

## Where it is
- App: the branch switcher in every workspace's bar (`workspace/ui/BranchScopeButton.kt`), the scope (`core/session/BranchScope.kt`),
  the working branch (`SessionManager.workIn`, `WorkingBranch`), and each workspace's two levels (below).
- Server: nothing changed yet (first round = screens). Every branch path (`/restaurants/{r}/branches/{b}/…`) is already open to
  anyone of that restaurant (`BranchPolicy` = restaurant access).

## First round, built 2026-11-23 (for the user to check)
- **The switcher.** A pill in the bar of POS, KDS, Inventory, Admin Hub, Statistics, Finance & taxation and Fraud Detection (on
  phones in the slim top bar): the branch looked at ("Blloku") or "All branches". Its menu: "All branches" with what it means in
  that workspace (not in the POS), then every branch (open ones first, "Your branch", closed ones greyed). Shown only to people
  who may see more than their branch (super admins; owners, co-owners and admins) and only with two or more branches; everyone
  else works as before, in their own branch.
- **The restaurant's home after signing in** (user, 2026-11-23: "I just log in, I can't choose a branch to enter"; then "I don't
  like this page; make it better; two sections: branches, and what it can have for all branches, like statistics"): owners,
  co-owners and admins of a restaurant with several branches land on `RestaurantHomeScreen`: the restaurant on its picture
  ("Welcome back, <name>", logo, branches and how many are open), then **Work in a branch** (a card per branch: picture with
  Open/Closed and "Your branch", name, address, open now, staff and devices, Work here → the workspaces at that branch) and
  **The whole restaurant** (the workspace picker's own cards, saying what each shows for every branch: Inventory, Admin Hub,
  Statistics, Finance & taxation, Fraud Detection, each opening straight at All branches). One branch: straight on to the
  workspaces. The workspace picker has a back arrow to this home and the restaurant + branch bar to switch.
- **Picking a branch** makes it the branch the person works in, in every workspace (super admins: the branch they entered;
  others: `SessionManager.workIn`, until they pick their own again or sign out). **All branches** is per workspace: Statistics
  can show every branch while the POS stays at Blloku.
- **POS:** branch level only; the menu says menus, prices and settings are set once for every branch (the central
  configuration lives in the Admin Hub and the Menu).
- **KDS:** a branch only, its kitchen as always (an "Every kitchen" monitor was built in the first round and taken out the
  same day: the kitchen is something you go inside the branch to see).
- **Inventory:** a branch = only its places and what's in them (stock, low stock, counts, history, sale places;
  `InventoryState.scopedTo`); All branches = everything, with "Stock in every branch" on the overview (each branch's places,
  items, worth, running low; places of no branch, like a central warehouse, as "Not at a branch"; a click works in that branch).
- **Admin Hub:** a branch = Shifts, Salary, Staff (Users named so, listing the people who work there and those of no branch),
  Devices, Settings, Audit log, Restaurant info (with that branch); All branches = Users (everyone, roles), Branches (the cards,
  each with its own page; Add branch for the Super Admin), Settings, Audit log, Restaurant info (the restaurant only).
- **Statistics:** follows the switcher (a branch, or every branch combined); its own branch picker shows only for people without
  the switcher.
- **Finance & taxation:** a branch = its own records, with a note that returns are filed and paid once for the whole
  restaurant; All branches = consolidated, with "Every branch in <month>" (sales, VAT, payroll taxes, profit per branch).
- **Fraud Detection:** follows the switcher (a branch's alerts, or across every branch).

## MOCK in the first round
- Working in another branch is kept on the device: path calls follow it, but the few server spots that use the person's own
  default branch (shift board and plans, My Sales, some settings, adding people) don't yet.
- Who sees the switcher: owners, co-owners and admins by role, until a permission of its own ("Work in every branch").
- Staff at a branch: filtered on the device from the loaded list; the role cards still count everyone.
- Inventory at a branch: filtered on the device; the overview's chart (stock in and out per day) still counts every branch.
- Finance: a branch's records are its share of the made-up restaurant figures.

## Decisions to take
- [x] Who may switch branches: the permission "Work in every branch" (`BRANCHES_ALL`, Admin Hub → Restaurant info; owner,
  co-owner and admin by default), 2026-11-23 (user: "the owner username should have only one branch": the demo `owner` has it
  taken away, `demo.bistro.owner` keeps it). Still open: all branches for someone without it?
  And can a manager have it for some branches only (the permission scopes of step 3)?
- [ ] People who work in two branches (a waiter in Blloku on Mondays): one default branch plus others they may work in?
- [ ] Shared or per branch: menus and prices (one menu for all, or a branch can change prices / hide dishes), settings
  (opening hours already per branch; others?), suppliers and items (shared catalogue, stock per branch?), roles (shared).
- [ ] Central warehouse: a place of no branch that sends stock to branches (transfers between branches).
- [ ] Salary at All branches (everyone's pay together) or only per branch.
- [ ] Statistics/Finance comparisons between branches (rankings, a branch against the average).
- [ ] What a phone shows for the switcher (now the slim top bar only in POS, Admin Hub and Inventory).

## 1. Screens
- [x] First round: the switcher and both levels in every workspace (2026-11-23), built and running.
- [x] Cards in "The whole restaurant" made the picker's small size (user, 2026-11-23: "they are so big").
- [ ] The user checks them after the first launch, with `MULTI_BRANCH_ON` true; changes asked go here.

## 2. Development (the feature complete, no tests)
- [ ] Server: accept the branch someone works in for owners/co-owners/admins (the `X-Branch-Id` header, checked against their
  restaurant and its open branches; `ActorScope.defaultBranchId`), and a permission for it.
- [ ] Server filters: people by branch (`/users?branchId=`), Inventory activity by branch, Finance's real records per branch.
- [ ] The places that use the person's default branch on the server follow the branch they work in.
- [ ] Replace the MOCKs above.

## 3. Hard testing list (run later, when the user says)
- [ ] Written once development is done.
- [ ] Run (later, when the user says).

## Done
- Nothing yet.

## Note from step 4 (2026-11-23)
- A branch's phone and email are kept the same as the restaurant's for now (Restaurant info → Details changes both; a branch's
  page sends its new phone/email to the restaurant too). With several branches each may want its own: decide here.
