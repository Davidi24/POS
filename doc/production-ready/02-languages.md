# 2. Languages

Status: Hard testing list ready (branch `languages-production`, 2026-11-19; screens waiting for your look) (Not started → Screens → Development → Hard testing list ready → Done)

Why here (2026-11-17): every later step's screens will put their words into the translation files as they're finished, so it's done
once per screen instead of going through every screen again at the end. The sign-in screens (step 1) are the first to be translated.

## Where it is
- App: the language button in the top bars (POS, the workspace picker; "EN" with a globe, `pos/ui/PosTopBar.kt` `LanguageSelector`).
- Server: account emails (`AuthMailService`), booking emails (`ReservationMailService`), the guest booking pages and the password page
  (served by the server), error messages shown in the app.

## How it is now
- The language button is a MOCK: it offers English, Spanish and Italian and changes nothing, and the choice isn't kept.
- Every text in the app is written in English inside the code (about 4,000 texts); there are no translation files yet.
- Emails, guest pages and server messages are English only. Dates and money are formatted in `core/format/Formatting.kt`.

## Known gaps
- Messages from the server (for example "Wrong username or password.") come in English; the app shows them as they are.

## Decisions to take
- [x] Which languages (user, 2026-11-18): English, Italian, Spanish and Albanian.
- [x] Whose choice (user, 2026-11-18: "each person makes a default in profile and then can change it any time in the nav bar"): each person sets their language in Profile → Preferences; the language button in the top bar switches it at any time on that device. Before anyone signs in, the sign-in screens use the language last used on the device (English the first time).
- [x] Guests (user, 2026-11-18: "English and the one they booked in, so English always included"): booking emails come in the language the guest booked in with an English version under it (just English when they booked in English); guest pages always offer English.
- [x] Who translates (user, 2026-11-18): I translate while building; a native speaker checks later and we fix what they find.
- [x] What restaurants type themselves (menu, places, roles) (user, 2026-11-18: "the things that need to be written manually, we do them later in menu"): waits for step 11 (Menu & recipes), with the proposal below.

## Proposed so far (2026-11-17, to confirm when this step starts)
- The app's own words are translated once by us; each person picks their language.
- What a restaurant types (menu, sections, options, role and place names, receipt texts): one main language required, other
  languages optional per item (a "Languages" tab in the editor, built in step 11 for dishes); anything not translated shows in the
  main language. Guests see their language where it exists, waiters their own, the kitchen always the main language.
- A "Translate" button fills the other languages for the owner to check, through our server: Microsoft Translator (free about
  2 million characters a month) first, Google Cloud Translation (500,000 free) as the backup; check their current terms and the
  quality for Albanian first.

## 1. Screens
- [x] The language button for real (the top bars and the sign-in screens, top right): English, Italiano, Español, Shqip, each in its own name.
- [x] Profile → Preferences → Language: the person's own language, saved on the server.

## 2. Development (the feature complete, no tests)
- [x] Every word the app writes itself in one table, `composeResources/files/i18n/strings.tsv` (en, it, es, sq), keyed by the English
  text: 6,003 texts, all translated. A drop-in `Text` looks each one up, so no screen had to change; texts with values match their
  row by `{}`, texts joined in the code are translated piece by piece, styled texts span by span, and chart labels, PDF reports and
  CSV headings too. The tool is `mobile_desktop/i18n/i18n.py` (stats, missing, add, prune), the words to keep the same are in
  `mobile_desktop/i18n/glossary.md`.
- [x] The chosen language saved for the person on the server (`users.language`, V124, `PUT /auth/me/language`) and used at sign-in
  (password and PIN); the device keeps the last one for the sign-in screens.
- [x] The server's messages are in the app's table, so they show in the person's language.
- [x] Account emails in the person's language; guest booking emails in the language they booked in with the English under it
  (`reservations.language`, V125; staff bookings English only); the password page, the email page and the guest booking pages
  in four languages with the choice at the top, English always there. The server's own table is `back-end/src/main/resources/i18n/server.tsv`
  (629 texts, all translated; `i18n.py --server`).
- [x] Day and month names in every date in the language in use (app and server).
- [ ] Numbers and money in each language's style (1.234,50 €): not done, it's in [decide-later.md](decide-later.md); every language
  shows €1,234.50 for now.
- [x] From here on, every step's screens use the table: new words get their rows with `i18n.py missing` / `add`.

## 3. Hard testing list (run later, when the user says)
1. The language button on the sign-in screen and in every top bar: the four languages in their own names; switching changes every
   screen at once; the choice is still there after closing and opening the app.
2. Profile → Preferences → Language saves for the person: signing in on another device (password and PIN) opens in it; a person
   who never chose one gets the device's language.
3. Every workspace in each language at phone, tablet and desktop width: nothing left in English except names and what the restaurant
   typed; no button, tab, card or nav entry cut or pushed out by the longer Italian, Spanish and Albanian words.
4. Texts with values and joined texts ("2 of 5 not sent", "Mon 6 Nov · 19:00", "Paid … €12.00. €3.00 left to pay.") read right.
5. Dates: day and month names in the calendar pickers, Reservations, Shifts, Statistics, History.
6. Statistics charts, the PDF reports (letters ë, ç, ñ, à print right) and the CSV headings in the language.
7. Server refusals (wrong password, a table already has an order, limits) show in the person's language.
8. Account emails for a person with Italian, Spanish, Albanian and none chosen: invitation, reset link, password changed, email
   confirmation and change, username changed, the two daily-limit emails.
9. The password page and the email page from those links: open in the person's language, switch at the top, form errors and the
   "done" page stay in the chosen language.
10. Guest pages: the booking form in the browser's language and with `?lang=`; a booking made in Italian keeps Italian on its page,
    cancel page, status and money lines; refusals in the language; English always offered.
11. Guest emails (once switched on): an Italian booking gets Italian with the English under it; an English or staff-made booking
    gets English only.
12. Words the restaurant typed that are also app words (a dish called "Bread") get translated too: see how much it shows.
13. Switching languages stays instant and long lists (orders, stock, history) scroll as smoothly as in English.
14. Amounts in another currency (globe → Show amounts in, or Profile → Preferences): pick US dollar in a euro restaurant and in a
    peso one; Tables, Orders, Menu, Statistics (cards, charts, Sales details), My Sales, Salary show "≈ $…"; the payment screen,
    salary Pay, inventory and menu editors, Admin Hub settings and downloads (PDF, CSV, card pictures) stay in the restaurant's
    currency, with the "≈ … just to give you an idea" line on payments; with the rate service unreachable nothing changes and
    the menu says "No rate right now"; switching restaurants (super admin) follows the new restaurant's currency.
15. A native speaker of each language goes through `strings.tsv` and `server.tsv` with the glossary; fix what they find.

## Done
- 2026-11-18/19: languages decided; language button and Profile → Preferences; `users.language` (V124) and `reservations.language`
  (V125); every app text (6,003) and server text (629) translated; emails and server pages in four languages; dates in each language.
