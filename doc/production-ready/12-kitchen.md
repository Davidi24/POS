# 12. Kitchen

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: KDS (Tickets, Upcoming, Menu, History), POS → Kitchen Status.
- Server: `/kds/*` (board, tickets, back, pos-timing), stations and routing.

## How it is now
- Tickets board: New, Cooking, Ready to pick up; the KDS never serves (the waiter serves from POS).
- Without stations the KDS says "The kitchen isn't set up yet".

## Known gaps
- Printing a ticket is a toast only.
- Held and timed tickets: the server never sets `due_at` itself (demo data set it by SQL).
- Where stations and routing are set up in the app.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
