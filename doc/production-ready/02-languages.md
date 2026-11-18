# 2. Languages

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

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
- Which languages (English plus which: Albanian, Italian, Spanish…?).
- Whose choice: each person picks their own, each device has one, or the restaurant sets one default (and people may change it).
- Guests: in which language their emails and booking pages come (the restaurant's language, or the one they booked in).
- Who translates: you, a translator, or a machine first and then checked by a person.

## Proposed so far (2026-11-17, to confirm when this step starts)
- The app's own words are translated once by us; each person picks their language.
- What a restaurant types (menu, sections, options, role and place names, receipt texts): one main language required, other
  languages optional per item (a "Languages" tab in the editor, built in step 11 for dishes); anything not translated shows in the
  main language. Guests see their language where it exists, waiters their own, the kitchen always the main language.
- A "Translate" button fills the other languages for the owner to check, through our server: Microsoft Translator (free about
  2 million characters a month) first, Google Cloud Translation (500,000 free) as the backup; check their current terms and the
  quality for Albanian first.

## 1. Screens
- The language button for real (in the top bars and on the sign-in screen), and where the restaurant's default is set (Admin Hub → Settings).

## 2. Development (the feature complete, no tests)
- Translation files in the app (Compose resources `values-xx/strings.xml`), the sign-in screens moved onto them first.
- The chosen language saved for the person on the server (and on the device for the sign-in screens before anyone signs in).
- Server answers carry a code the app translates (sign-in already sends `code`); emails and guest pages in the right language.
- Dates, numbers and money written the way each language writes them.
- From here on, every step's screens use the translation files.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
