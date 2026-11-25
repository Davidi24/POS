# 24. Web

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

The last step (user, 2026-11-23: "the web is the last thing we'll do; after the app is finished, then the web").

Why last (user, 2026-11-17: "add 23 which is web, we will do it in the end"): the web pages show what the app and the server
already do, so they're built once everything else is final.

## Where it is
- `website/`: the guest website (Next.js), a home page and a menu page so far.
- `web/`: web apps started as folders only (`admin`, `kds`, `pos`, `storefront`, `superadmin`), nothing in them yet.
- Pages the server serves itself (already working): set a password from an emailed link (`/public/account/password`), confirm an
  email (`/public/account/email`), the guest booking pages (`/public/book/…`, `/public/bookings/…`).

## To do (from earlier steps)
- The page where a new restaurant applies and checks its application by its code (moved here from step 1): the sign-in screen's
  "Don't have an account? Click here" opens it (`SignUpPage.url()`, today `<server>/public/apply`, not built yet).
- The guest website for each restaurant: menu, booking, opening hours.
- Admin Hub → Settings → Online booking (says "coming later" today): what it holds comes with the website (moved here from
  Settings on 2026-11-24).

## Decisions to take
- Which of the `web/` apps are really needed (a browser POS, KDS or Admin, or the app alone).
- One website per restaurant (their own address) or one site for all of them.
- Where the web pages live (own domain, hosting).

## 1. Screens
- To agree with the user when this step starts.

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
