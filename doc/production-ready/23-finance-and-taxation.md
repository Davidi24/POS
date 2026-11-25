# 23. Finance & taxation

Status: Screens (Not started → Screens → Development → Hard testing list ready → Done)

Added 2026-11-23 (user: "a change of plans: one more app, finance and taxation; I want to handle taxation in the app, so a
restaurant doesn't need to hire someone; the app helps doing it"). It comes right before Web, the last step (user,
2026-11-23: "make finance and taxation before the web; the web is the last thing, after the app is finished").

## Where it is
- App: a seventh workspace card, "Finance & taxation" (`Workspace.FINANCE`, after Statistics, before Fraud Detection), places
  Overview, VAT, Expenses, Payroll, Deadlines, Tax returns, Tax setup (`finance/FinanceTab`, `finance/ui/*`).
- Server: nothing yet. Everything shown is MOCK, worked out on the device (`finance/FinanceBook.kt`).

## How it works (first screens, 2026-11-23)
- Every month in four steps (shown on the Overview): the app works the taxes out from sales, purchases and wages; you check
  the list of what's missing; you file the return with the numbers it gives; you pay and mark it. Reminders before each
  deadline.
- Overview: "Next to pay" (amount, due day, everything due that month), four figures (VAT to pay, payroll taxes, profit tax
  this year, how much to keep aside), Coming up, Before you file (missing VAT numbers, receipts, personal numbers, dishes
  without a VAT rate, each with Fix), how the month adds up, taxes month by month.
- VAT: on sales by rate, on purchases by supplier, to pay or get back, the return's state with Mark as filed / paid, the sales
  book (a line a day) and the purchase book (a line an invoice).
- Expenses: every cost with its invoice (food and drink from Inventory deliveries, bills added here), by kind, filters, Add
  receipt, Add expense (the right-edge tab: who, kind, amount with VAT, VAT rate, invoice number, day, receipt).
- Payroll: each person's wage, contributions held back, income tax, net pay and what the restaurant adds; the rates used.
- Deadlines: by month, with Mark as filed / paid; done ones fold away.
- Tax returns: Month ends → Check → File → Pay; the numbers for the VAT return and the payroll list box by box; documents
  (VAT return, sales book, purchase book, payroll list, profit and loss) to download or send to the accountant; the year's
  profit tax.
- Tax setup: the restaurant's legal name and tax numbers (real, moved here from Restaurant info's Legal & tax on 2026-11-23;
  only the Super Admin changes them), country, VAT registered, monthly or quarterly, VAT rates, due days, reminders, contributions, income tax bands,
  profit tax (or small business), % of sales to keep aside, the accountant's email. Defaults are Albania's as the app knows
  them (VAT 20 % / 6 %, returns by the 14th, payroll by the 20th, contributions 9.5 % + 1.7 % and 15 % + 1.7 %, profit tax
  15 %), to be checked with an accountant.
- MOCK: the figures, the people and suppliers, filing marks, added expenses and the setup live in memory (`FinanceBook`);
  opening the workspace needs SETTINGS_MONEY until it has its own permission; the card's picture is `role_finance` until the
  user's own; downloads and sending say "comes with the production pass".

## Decisions to take
- [x] Where this step goes: step 23, right before Web (user, 2026-11-23).
- [ ] Which countries first: Albania (the demo restaurants) and its rules checked by an accountant; others later.
- [ ] Filing: the app prepares everything and the person types it into e-Filing (now), or the app sends it to the tax office
  itself (needs the tax office's connection and certification; much later).
- [ ] Statistics → Finance (Balance, Money in, Money out) overlaps: keep it there, or move it here.
- [ ] Who opens it: its own permission (owner by default; Admin? the accountant as a person with only this workspace?).
- [ ] VAT per dish: by menu section, by dish, or one rate for everything (the server has no VAT per line yet).
- [ ] Expenses: receipt photos, a bank statement import, recurring bills (rent every month).
- [ ] The workspace card's picture (the user's own, like the others).

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: all of Finance & taxation needs the password.
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- [x] First version of every place on MOCK figures (2026-11-23), built and running.
- [ ] The user looks at them; changes asked go here.

## 2. Development (the feature complete, no tests)
- [ ] The feature complete, after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- [ ] Written once development is done.
- [ ] Run (later, when the user says).

## Done
- Nothing yet.
