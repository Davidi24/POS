# Permissions — final decisions, one place at a time

Step 3 (Users & roles). Generated from the code. Each workspace starts with its **Open …**; each place of its bar is a
card whose switch is its **See …** (off = not in their bar at all); under it, what changes things there. Seeing only
looks: every change has its own permission. Switching something off and on again brings back what was on under it.

- ✓ = has it when a restaurant starts · – = doesn't · 🛡 = owner-level, asks "Be careful" first · *(where … / which
  roles)* = can be given for some of them only (Choose in the card)
- Starting and cancelling an order come with **Edit orders** (not shown on their own).
- Owner always has everything. An admin also gets the roles under it by default; the Admin column is the Admin role only.

Columns: **Ow** Owner · **Co** Co-Owner · **Ad** Admin · **Ma** Manager · **Wa** Waiter · **Vi** Viewer · **Ki** Kitchen ·
**Cl** Cleaner · **Ex** Extra worker


## POS (decided 2026-11-20)

| Place | Permission | What it lets them do | Needs | Ow | Co | Ad | Ma | Wa | Vi | Ki | Cl | Ex |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| – | Open POS | Open the POS workspace | – | ✓ | ✓ | – | ✓ | ✓ | ✓ | – | ✓ | ✓ |
| Tables | See tables *(where: branches, floors, places)* | The floor plan, on the floors chosen for them | Open POS | ✓ | ✓ | – | ✓ | ✓ | ✓ | – | – | ✓ |
| Tables | Change tables | Seat guests, set tables free or out of use, move guests and join tables | See tables | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Tables | Take payments | Request the bill and take the payment from a table | See tables | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Tables | Edit the floor plan | Add, move and remove tables, floors and places | See tables | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Tables | Open outside the hours | Open the branch for now while it's closed: a late group, an event | See tables | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Orders | See orders | The open orders and what's on them | Open POS | ✓ | ✓ | – | ✓ | ✓ | ✓ | – | – | ✓ |
| Orders | Edit orders | Start orders, add and remove dishes, notes, send to the kitchen, serve, cancel unpaid-for orders | See orders | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Orders | Take payments | Request the bill and take the payment for an order | See orders | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Orders | Close as unpaid 🛡 | Close an order whose guests left without paying | See orders | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Orders | Void what was sent 🛡 | Void orders or dishes the kitchen already has | See orders | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Orders | Move orders to another table | Move an order to another table | See orders | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Orders | Give discounts | Take money off a dish or a whole order | See orders | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Orders | See an order's kitchen status | The food status of an order and of its dishes | See orders | ✓ | ✓ | – | ✓ | ✓ | ✓ | – | – | ✓ |
| Reservations | See reservations | Bookings, the overview and free tables for walk-ins | Open POS | ✓ | ✓ | – | ✓ | ✓ | ✓ | – | – | – |
| Reservations | Make and change bookings | Book, change time, guests, notes and table, confirm attendance, check in and seat | See reservations | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Reservations | Cancel bookings | Cancel a booking or mark a no-show | See reservations | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Reservations | Approve bookings | Accept or decline booking requests and move bookings to other tables | See reservations | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Reservations | Fix past bookings | Correct bookings after the time limits or from an earlier day, with a reason | See reservations | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Reservations | Give goodwill refunds 🛡 | Give back part of money the restaurant kept, like a late cancel's deposit | See reservations | ✓ | – | – | – | – | – | – | – | – |
| Menu | See the menu | Menus, sections, dishes and their prices | Open POS | ✓ | ✓ | – | ✓ | ✓ | ✓ | – | – | ✓ |
| Menu | Mark sold out | Mark a dish or an option sold out and available again | See the menu | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| Menu | Add dishes and menus | Add menus, sections, dishes, variants and options | See the menu | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Menu | Change dishes and menus | Change dishes, photos, ingredients, the order and what waiters see, not prices | See the menu | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Menu | Change prices 🛡 | Change the price of dishes, variants and options | Change dishes and menus | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| Menu | Delete dishes and menus | Delete menus, sections, dishes, variants and options | See the menu | ✓ | ✓ | – | – | – | – | – | – | – |
| Kitchen Status | See Kitchen Status | The whole kitchen board: where every order is | Open POS | ✓ | ✓ | – | ✓ | ✓ | ✓ | – | – | ✓ |
| My Sales | See My Sales | Their own sales and tips | Open POS | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| History | See history | Closed, cancelled and unpaid orders, their bills, printed again | Open POS | ✓ | ✓ | – | ✓ | ✓ | ✓ | – | – | – |
| History | Reopen for payment | Reopen an unpaid order to take its payment | See history | ✓ | ✓ | – | ✓ | ✓ | – | – | – | – |
| History | Reopen orders 🛡 | Reopen a closed order | See history | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| History | Refund payments 🛡 | Give money back on a paid order, with a reason | See history | ✓ | ✓ | – | ✓ | – | – | – | – | – |
| History | See what changed on orders | Every change made to an order, by whom and when | See history | ✓ | ✓ | – | ✓ | – | – | – | – | – |

## Kitchen display (to decide next)

| Place | Permission | What it lets them do | Needs | Ow | Co | Ad | Ma | Wa | Vi | Ki | Cl | Ex |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| – | Open Kitchen Display | Open the kitchen display workspace | – | ✓ | ✓ | – | – | – | ✓ | ✓ | – | – |
| Tickets | See tickets | The tickets on the kitchen board | Open Kitchen Display | ✓ | ✓ | – | – | – | ✓ | ✓ | – | – |
| Tickets | Move tickets | Start dishes, mark them ready and send tickets back | See tickets | ✓ | ✓ | – | – | – | – | ✓ | – | – |
| Upcoming | See Upcoming | Food ordered ahead for bookings and timed orders | Open Kitchen Display | ✓ | ✓ | – | – | – | ✓ | ✓ | – | – |
| Menu | See the menu | The menu, as the POS shows it | Open Kitchen Display | ✓ | ✓ | – | – | – | ✓ | ✓ | – | – |
| History | See history | Tickets that went out | Open Kitchen Display | ✓ | ✓ | – | – | – | ✓ | ✓ | – | – |

## My shift (decided 2026-11-20; shown in the POS and on the kitchen display)

| Place | Permission | What it lets them do | Needs | Ow | Co | Ad | Ma | Wa | Vi | Ki | Cl | Ex |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| My shift | My shift | Their own shifts: clock in and out, take breaks | – | ✓ | ✓ | – | ✓ | ✓ | – | ✓ | ✓ | ✓ |
| My shift | See their pay | Their hours, wage or salary and tips | My shift | ✓ | ✓ | – | ✓ | ✓ | – | ✓ | ✓ | ✓ |
| My shift | See others' shifts *(which roles)* | Who works when, for the roles chosen for them | My shift | ✓ | ✓ | – | ✓ | ✓ | – | ✓ | ✓ | ✓ |

## Inventory (to decide)

| Place | Permission | What it lets them do | Needs | Ow | Co | Ad | Ma | Wa | Vi | Ki | Cl | Ex |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| – | Open Inventory | Open the inventory workspace | – | ✓ | ✓ | ✓ | ✓ | – | ✓ | ✓ | – | – |
| Overview | See the overview | Stock health, value and the stock in and out chart | Open Inventory | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Stock | See stock | What's in stock, where, and what's running low | Open Inventory | ✓ | ✓ | ✓ | ✓ | – | ✓ | ✓ | – | – |
| Stock | Receive deliveries | Put deliveries in stock | See stock | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Stock | Record waste | Take out what was thrown away | See stock | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Stock | Move stock | Move stock from one place to another | See stock | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Stock | Correct stock | Correct amounts and send things back to suppliers | See stock | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Stock | Manage places | Add, change and delete places and where dishes take stock from | See stock | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Items | See items | Every item and sub-recipe with its cost, supplier and levels | Open Inventory | ✓ | ✓ | ✓ | ✓ | – | ✓ | ✓ | – | – |
| Items | Add items | Add items and sub-recipes | See items | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Items | Change items | Change items, their levels, photos and kinds, and sub-recipes | See items | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Items | Delete items 🛡 | Delete items; what's left in stock is written off | See items | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Counts | See counts | Stock counts and their differences | Open Inventory | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Counts | Count stock | Start counts, write what was counted and finish them | See counts | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Counts | Approve counts 🛡 | Approve a count, which changes the stock to what was counted | See counts | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| History | See history | Every stock change and what happened to items | Open Inventory | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Suppliers | See suppliers | Suppliers, their places, the items bought from them and orders | Open Inventory | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Suppliers | Change suppliers | Add and change suppliers, their items and prices, and make orders | See suppliers | ✓ | ✓ | ✓ | – | – | – | – | – | – |

## Admin Hub (to decide)

| Place | Permission | What it lets them do | Needs | Ow | Co | Ad | Ma | Wa | Vi | Ki | Cl | Ex |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| – | Open Admin Hub | Open the admin hub workspace | – | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Shifts | See shifts | Who works when, and who's clocked in | Open Admin Hub | ✓ | ✓ | ✓ | ✓ | – | – | – | – | – |
| Shifts | Plan shifts | Plan shifts, put people on them and fix clock-ins | See shifts | ✓ | ✓ | ✓ | ✓ | – | – | – | – | – |
| Salary | Salary 🛡 | See wages and tips, set how people are paid and record payouts | Open Admin Hub | ✓ | ✓ | – | – | – | – | – | – | – |
| Users | See people | The people working here and their roles | Open Admin Hub | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Users | Add people | Add people and choose their roles | See people | ✓ | ✓ | ✓ | ✓ | – | – | – | – | – |
| Users | Change people | Change details, roles, permissions and PINs, and reset passwords | See people | ✓ | ✓ | ✓ | ✓ | – | – | – | – | – |
| Users | Remove people | Remove people from the staff | See people | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Users | Sign people out 🛡 | See where people are signed in and sign them out | See people | ✓ | ✓ | – | – | – | – | – | – | – |
| Roles | See roles | The roles and what each can do | Open Admin Hub | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Roles | Make roles 🛡 | Make the restaurant's own roles | See roles | ✓ | ✓ | – | – | – | – | – | – | – |
| Roles | Change roles 🛡 | Rename roles, change their pay and switch them off | See roles | ✓ | ✓ | – | – | – | – | – | – | – |
| Roles | Delete roles 🛡 | Delete the restaurant's own roles | See roles | ✓ | ✓ | – | – | – | – | – | – | – |
| Roles | Change what roles can do 🛡 | Switch a role's permissions on and off | See roles | ✓ | ✓ | – | – | – | – | – | – | – |
| Settings | See settings | How the restaurant is set up | Open Admin Hub | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Settings | Change settings | Bookings, tables, orders, shifts, inventory and other settings | See settings | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Settings | Change payment and fraud settings 🛡 | Tax, service charge, paying, receipts and fraud checks | See settings | ✓ | ✓ | – | – | – | – | – | – | – |
| Audit log | See the audit log | Every change made, by whom and when | Open Admin Hub | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Devices | Manage devices 🛡 | Add, connect and remove tills, tablets, printers and other devices | Open Admin Hub | ✓ | ✓ | – | – | – | – | – | – | – |
| Restaurant info | See restaurant info | The restaurant's details, branches, logo and currency | Open Admin Hub | ✓ | ✓ | ✓ | ✓ | – | ✓ | – | – | – |
| Restaurant info | Change restaurant info 🛡 | Description, phone, email, addresses, logo, colours and receipt texts | See restaurant info | ✓ | ✓ | – | – | – | – | – | – | – |
| Restaurant info | Change opening hours | Each branch's week and its special days | See restaurant info | ✓ | ✓ | ✓ | ✓ | – | – | – | – | – |

## Statistics (to decide)

| Place | Permission | What it lets them do | Needs | Ow | Co | Ad | Ma | Wa | Vi | Ki | Cl | Ex |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| – | Open Statistics | Open the statistics workspace | – | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Overview | See the overview | Sales, orders and guests at a glance | Open Statistics | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Sales | See Sales | Sales by day, hour, service and floor | Open Statistics | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Finance | See Finance 🛡 | Money in and out, wages, refunds, tips and tax | Open Statistics | ✓ | ✓ | – | – | – | – | – | – | – |
| Orders | See Orders | How many orders, of what kind, and when | Open Statistics | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Dishes | See Dishes | Every dish sold, its cost and margin | Open Statistics | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Stock | See Stock | Stock in and out, food cost and waste | Open Statistics | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Tables | See Tables | Bookings, guests and how tables were used | Open Statistics | ✓ | ✓ | ✓ | – | – | – | – | – | – |
| Team | See Team | Sales, hours and tips by person | Open Statistics | ✓ | ✓ | ✓ | – | – | – | – | – | – |

## Fraud Detection (to decide)

| Place | Permission | What it lets them do | Needs | Ow | Co | Ad | Ma | Wa | Vi | Ki | Cl | Ex |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| – | Open Fraud Detection 🛡 | Open the fraud detection workspace | – | ✓ | ✓ | – | – | – | – | – | – | – |
| Alerts | See alerts 🛡 | Flagged discounts, refunds, removals and other risky actions | Open Fraud Detection | ✓ | ✓ | – | – | – | – | – | – | – |
| Alerts | Review alerts 🛡 | Mark flagged actions as checked, dismissed or confirmed | See alerts | ✓ | ✓ | – | – | – | – | – | – | – |

## Only the Super Admin's

Not shown in a restaurant's windows: Create restaurants, Delete restaurants, Export settings, Import settings, Manage
settings templates, Apply settings templates.

