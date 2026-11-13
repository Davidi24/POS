# Rules

How the POS behaves, one area at a time. These rules are already built into the app and the server.

## Tables

### Joining tables

You start a join from one table, then pick the tables to join to it. Anyone who takes orders (waiters too) can join and unmerge tables.

**Who can join whom.** A table can take in tables of its own kind or lower:

| Rank | Table |
|---|---|
| 1 (lowest) | Free |
| 2 | Reserved, including a late booking |
| 3 | Guests seated, no dishes ordered yet (an order with no dishes counts here) |
| 4 (highest) | Has an order with dishes |

| Start table | Can take in |
|---|---|
| Free | Free tables only |
| Reserved | Free and reserved tables |
| Guests seated | Free, reserved and seated tables |
| Has an order | All of the above, and other tables with an order |

A free table can never take in a reserved one. Start from the reserved table instead.

**Never joined, either way:**
- A table whose bill was requested (Bill Pending)
- A table that has paid and is waiting for the guests to leave (Paid)
- An unavailable table
- A table where a payment has already started
- A table on another floor
- A table already in another group (change that group with **Merge new**)

**Warnings before joining:**
- Joining a table that has an order: the orders become **one order** on the start table, and the kitchen keeps cooking the dishes already sent. A joined table's order with no dishes is cancelled.
- Joining a table with a booking coming (within 2 hours; Settings → Tables → Special cases can change this): the app shows the booking and asks. People who may move bookings (managers and up) can pick a free table for it, then **Move and join**. Otherwise **Join anyway**: the booking stays on that table, and the table warns when it's due. The start table's own booking never asks.

**While joining:** tables that can't be picked are faded. Tapping one shows a yellow message saying why.

### Seated or ordered

- A table turns blue (Ordered) only once its order has a dish. Right after **Start order**, or when every dish was cancelled, it stays orange (Occupied).
- An orange table can be set free, after an "are you sure". If it has an order with no dishes, that order is cancelled.

### A merged group

- The joined tables act as one table: one state, one ring, one order.
- Seating a group needs more guests than its biggest table holds. Two tables of 4 need at least 5 guests.
- A note on a merged group (like "Booking 14:18") shows above its highest table.
- Hovering over or holding a merged table highlights its group. The dashed lines between merged tables always show.
- While any table is hovered or held, only its own note shows. The other tables' notes come back when you let go.
- **Merge new** adds tables to the group or takes them out. It isn't available while the bill is requested.

### Unmerging

**Nobody seated (free or reserved tables):** the tables just separate.

**Guests seated, an order, or the bill requested:**
- The tables you unmerge become **free**.
- The guests and the order stay on **one** table:
  - **Two tables:** you choose which table keeps the guests and the order. The other becomes free.
  - **Three or more, All of them:** you choose which table keeps everything. All the others become free.
  - **Three or more, Some of them:** you pick the tables to free. Any table can be picked, the main one too, but at least one must stay. The main table keeps the order if it stays; if not, the biggest table left does.
- The guests are spread over the tables that stay. The guest total stays as it was, even if some guests left: it records how many came.
- If the bill was requested, it stays requested.
- The order's history says which tables were freed and where the order went, for example: "T25 unmerged and free; moved from T25 to T26".
- There is always one order: if a group ever held two, unmerging makes them one.

**Paid (waiting for the guests to leave):** you can't unmerge. **Set free** frees the whole group.

**Unavailable:** you can't unmerge.

### Rings

- **Free with a yellow ring:** a booking is coming to this table soon, so don't seat walk-ins there. It shows from 30 minutes before the booking (Settings → Tables → Special cases → "Show a coming booking from") until the booking's hold ends.
- **Reserved:** only a yellow ring, when the guests are late and the booking is about to go out.
- **The booking's occasion** (🎂 for a birthday) shows beside a table from its yellow ring on: while the ring is on, while the table is held for it or the booking is due, and then the whole time its guests are seated there. A booking later in the day shows nothing on the table yet. Opening the table says what it is: the occasion, whose booking, its time and how many guests. Guests who have come are seated, so the table turns orange; tick **More will come** if others are still on their way.

### The table panel

Dishes are added in the order, not on the table. **View order** opens that table's order, and you add the dishes there.

A table has **one open order** at a time, always:
- A second order on it is refused.
- An order can't be moved or split onto a table that already has one. A split with no table named gets no table.
- Reopening a closed order whose table has a new order since reopens it without a table.
- Joining tables combines their orders into one.

**Close as unpaid** is done in the order (Orders), not from the table.

Starting an order and adding nothing keeps the table orange, and its button still says **Start order**. Clicking it again opens the same order, with the same number.

A table with a booking coming shows a yellow warning with a **Handle** button. The ways to handle it (Move the booking, Move these guests, Give more time, Booking waits) open after you click it.

The table's bookings show as cards at the top of its panel: **Their booking** for the guests seated there, **Next booking** for the one coming. Tapping a card opens that booking in Reservations.

**Moving a booking to another table** always opens the floor plan: the free tables that fit are green, the best one is picked first, and you can tap another. This is the same from the table's **Move**, from **Handle → Move the booking**, when joining tables, and from Reservations.

### Moving guests

Guests move only to a **free** table, and the new table's seating limits must allow that many guests. Their order moves with them.
