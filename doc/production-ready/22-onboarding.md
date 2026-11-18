# 22. Onboarding

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

Why last (2026-11-17): it guides a new restaurant through screens from almost every other step (restaurant details, branches and hours,
tables, menu, staff, devices, payments, settings), so it's built when all of them are final.

## Where it is
- Starts after a restaurant is approved: the owner gets the invitation email, sets a password and signs in for the first time.
- Linked to step 1's web application page ("Don't have an account? Click here").

## How it is now
- A new owner lands in an empty restaurant (no menu, tables, staff or devices) and nothing tells them where to start.
- The app has one-time tips on single screens (`DeviceHints`, e.g. "Select a table to edit it."), but no first-time guide.

## Decisions to take
- A checklist the owner works through in any order, or a step-by-step setup on first sign-in.
- What's in it and in which order: restaurant details and logo, branches and opening hours, floor plan and tables, menu (or a sample
  menu to start from), staff and roles, devices and printers, payment methods and tax, the main settings.
- Which parts can be skipped, and whether it can be opened again later (from Admin Hub).
- A short first-time tour for staff too (a waiter's first sign-in), or only for owners.

## 1. Screens
- "Get your restaurant ready": the list with what's done and what's left, each line opening the real screen; the moment it's ready to open.

## 2. Development (the feature complete, no tests)
- What's done worked out from the restaurant's data (has a menu, tables, staff…), so nothing has to be ticked by hand.
- Shown to the owner until finished or put away.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
