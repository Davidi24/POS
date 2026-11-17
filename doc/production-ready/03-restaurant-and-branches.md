# 3. Restaurant & branches (plus opening hours)

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Restaurant info; Super Admin: restaurant list, branch list, a restaurant's page (Details, Branches, Places, Branding, Legal, Money).
- Server: `/restaurants`, `/restaurants/{r}/branches`, currency and time zone preview/change, owner add/invite/transfer, logo and covers, branch removal check, `/branches/{b}/business-hours`, `/special-hours/calendar`.

## How it is now
- Finished on 2026-10-24 (the user's checklist was built and tested): required phone and email, ownership transfer, archiving retires devices, public applications with review, reserved codes, paged branch chooser.
- The owner changes contact details, addresses, logo, colours and receipt texts; only super admins change name, legal name, code, status, tax profiles and branches.

## Known gaps
- Opening hours and special days exist on the server but the app doesn't show or use them (Statistics guesses closed days from the sales).

## To do in this step
- A quick re-check of what was finished.
- Opening hours and special days: where they're set, and used by Statistics (and later by bookings).

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
