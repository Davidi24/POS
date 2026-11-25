# Decide later

Things suggested during the production pass that are still waiting for your decision (user, 2026-11-18: "I'll see them at
the end"). Each one has what it is, the suggestion, and the step it belongs to. Nothing here is built until you decide.

## Step 1 — Sign-in & accounts

1. **Tell the owner or manager when an account gets locked.**
   When someone's account locks after 8 wrong passwords, the chosen roles get a notification (the bell and a yellow pop-up,
   like unpaid orders). It could mean someone is guessing that person's password, or that the person forgot it and needs help.
   Suggestion: yes, with "Who is told" in Admin Hub → Settings (Owner and Manager by default).
   Also to decide: should a till that pauses after 5 wrong PINs tell them too?
   → Comes up in step 6 (Notifications) (2026-11-24).

2. **Which limits a restaurant can change in Admin Hub → Settings.**
   Today they're fixed: 8 wrong passwords lock an account for 15 minutes; a sign-in lasts 30 days without use; 5 devices per
   person; a PIN has 5 digits; 5 wrong PINs pause a till for 15 minutes; a till locks after 30 seconds (3 minutes in
   important work).
   Suggestion: the till lock times and the PIN pause become settings (they depend on how a restaurant works); the password
   and sign-in limits stay fixed for everyone (they're about safety, not about how a restaurant works).
   → Comes up in step 5 (Devices & printing) (2026-11-24).

3. **A Manager connecting tills.**
   Today connecting a till needs "change settings" (Admin and up). An owner can give that to a Manager, but it gives them
   all of Settings.
   Suggestion: a separate "Connect tills" permission the owner can tick for a Manager, without the rest of Settings.
   → Comes up in step 5 (Devices & printing) (2026-11-24).
   → Decided 2026-11-25: "Connect devices" (Owner, Co-Owner, Admin by default; can be given to a Manager). Built in step 5.

4. **Typing a pairing code on a new till.**
   Today a till is connected from Admin Hub → Devices while signed in on it. The other way: a manager makes a code on any
   device and types it on the new till's sign-in screen (a small "Set up this device as a till" link).
   Suggestion: add it when the first real restaurant sets up its tills.
   → Decided 2026-11-25 (user: "connect with the code, both"): the sign-in screen's "Connect with a code". Built in step 5.

5. **The hidden server box in the phone app** (with the server address, waiting for the domain).
   A secret spot in the app to change which server it talks to, for trying a new version on a test server.
   Question: only in the test copies you get, or also in the app restaurants install? If restaurants have it, a curious
   waiter could type a wrong address and the till would stop working.
   Suggestion: only in test copies.

6. **A "not a robot" check on the restaurant application** (now a web page, step 24).
   Applications are already limited to 5 an hour per network address and 3 a day per email, and each one waits for your
   review. Suggestion: add the check only if fake applications start coming in.

## Step 2 — Languages

1. **Numbers and money in each language's style.**
   Today every language shows €1,234.50. Italian, Spanish and Albanian usually write 1.234,50 €.
   Suggestion: switch the display to each language's style, but only after checking that no form reads a shown amount back
   (a price field filled from a shown amount would misread "12,50").

2. **The guest's language when staff take a booking by phone.**
   Today a booking made by staff gets its emails in English only; only online bookings know the guest's language.
   Suggestion: a small "Guest's language" choice in the new-booking form, English by default.

3. **The globe in the top bar and Profile.**
   Today the globe changes the language on that device only; Profile → Preferences changes the person's own (used when they sign
   in anywhere). Question: should the globe also save to Profile?
   Suggestion: keep them apart, so a shared till can be switched for one shift without changing anyone's Profile.

4. **Restaurant applications' emails.**
   The application emails go out in English: whoever applies has no account or language yet.
   Suggestion: when the web form is built (step 24), it sends the page's language and the emails follow it.

## Step 4 — Restaurant & branches

1. ~~**Today's exchange rate in the currency change.**~~ Decided 2026-11-20: yes, filled in and can be changed (step 4).
   Today the person changing a restaurant's currency types the rate themselves. Frankfurter (`api.frankfurter.dev/v2/rates`,
   free, no key; it has the lek and the Dominican peso, among others) could fill it in: "Today's rate: 1 EUR = 90.29 ALL ·
   19 Nov".
   Suggestion: the server asks for it (once a day, kept), the app shows it filled in and the person can still change it. If the
   service doesn't answer, the field stays empty and they type it, as today.

2. **The person who reported a problem sees its answer.** (2026-11-21) → Decided 2026-11-21: in step 6 (Notifications).
   Anyone in a restaurant can now send "Report a problem" from the profile menu, and it goes on the Super Admin's Requests board.
   They can't see it again yet: not where it stands, who has it, or how it was solved.
   Suggestion: "My problems" under the same menu item, a short list with the stage (Open, Assigned, In progress, Solved) and the
   answer; a notification when it's solved.

3. **Tell the support admin when a request is given to them.** (2026-11-21) → Decided 2026-11-21: in step 6 (Notifications).
   Assigning a request to a support admin changes nothing for them until they open Requests.
   Suggestion: a notification (and an email) "<restaurant>: a problem was given to you", opening the ticket.

4. ~~Social links kept by the server.~~ Decided 2026-11-23: done in step 4's second PR. (2026-11-23)
   Restaurant info → Details now has Instagram, Facebook, TikTok, WhatsApp and Google Maps, but they're kept only on the device
   that typed them, and the booking website doesn't show them yet.
   Suggestion: five optional fields on the restaurant (`PUT /restaurants/{r}`), shown as small icons on the booking website and
   at the end of booking emails.

5. ~~People to contact.~~ Decided 2026-11-23: they stay hidden (the server keeps them). (2026-11-23)
   Restaurant info no longer shows "People to contact" (accounting, support, a manager); the server still keeps them.
   Suggestion: leave them out for good, or bring them back under Finance & taxation (the accountant) if the user wants them.

6. ~~A branch's exact location kept by the server.~~ Decided 2026-11-23: done in step 4's second PR. (2026-11-23)
   The address can now take an exact location (latitude, longitude), but it's kept only on the device that typed it.
   Suggestion: two optional columns on the branch's address (`latitude`, `longitude`), sent with the address; later the booking
   website can show a map pin and "Get directions".

7. **Which map to use in production.** (2026-11-23)
   Restaurant info → Details → Map shows OpenStreetMap's own tiles (free, light use only, credit on the map). Many restaurants
   opening it every day would be too much for their server.
   Suggestion: a map service with a key (MapTiler, Mapbox or Google Maps) through our server, decided with the website (step 24),
   which will show the same map to guests.

8. ~~The restaurant's colours.~~ Decided 2026-11-23: done in step 4's second PR. (2026-11-23)
   The Colours card was taken out of Logo & receipts (user: "useless"). The two colours already saved stay on the server, and
   the guest booking page may still tint itself with the main one.
   Suggestion: drop them on the server too (and from the booking page), or bring them back with the website (step 24) if the
   website wants a brand colour.

9. ~~Keep the receipt design on the server.~~ Decided 2026-11-23: done in step 4's second PR. (2026-11-23)
   Logo & receipts now has a receipt designer (a picture behind with its opacity and place, the line style, logo, barcode, QR
   code and its line), but it's kept only on the device that made it, and nothing prints yet.
   Suggestion: save it with the restaurant's branding (the picture in the same storage as the logo), and have the printers
   (Devices) print it when printing is built.

## Step 5 — Devices & printing

1. **A till already signed in that leaves the restaurant's network.**
   Since 2026-11-24 a PIN sign-in works only on the restaurant's network. A till that is already signed in isn't stopped when it
   leaves; it locks itself after 30 seconds without use anyway, and its PIN sign-in lasts 12 hours at most.
   Suggestion: leave it. Checking the network on every refresh would sign every till out at once in the middle of service the
   moment the restaurant's internet address changes.

2. **Tell the owner when a device is turned away from another network.**
   Today it shows only in Settings → Devices & printing → "Tried from other networks" and in the Audit log.
   Suggestion: a notification to the owner ("Till 1 tried from another network"), so they can allow the new network at once when
   the restaurant's internet changed. Belongs with step 6 (Notifications).

3. **What a PIN sign-in can't do (password only).** Decided and built 2026-11-25 (user: "I agree with all of the things you said, build it"); each step file has a "PIN or password (check here)" part to adjust screen by screen.
   User, 2026-11-25: "if someone logs in with PIN he should have limited things; things with a security risk need the password;
   do it all together, it's a lot of work; what a waiter or the kitchen display does should not be blocked, like void an order."
   Agreed so far: service work stays on PIN (POS, kitchen display, My shift, clock-in, looking at stock), voids, refunds,
   discounts and closing unpaid included, each still needing the person's own permission. First rule written but not built:
   connecting devices needs the password (server `security/rbac/PinSignIn`, mark PIN_SIGN_IN on a PIN sign-in's permissions;
   app `DevicesState.pinSignIn`).
   Proposed password-only list, waiting for the user's yes: all device management and the network rules; adding, removing or
   switching off staff, roles and permissions, resetting someone's PIN, password links; your own password, email, username,
   phone, PIN and your other devices; money settings (payments, billing, fiscal and tax, receipt design, fraud limits, currency,
   time zone); salary (rates, payouts, marking paid); restaurant info and branches; the other Admin Hub settings; downloads and
   exports; marking fraud checks reviewed; the audit log. Open: menu price changes, stock adjustments and waste, approving
   counts, opening hours ("Open now" stays on PIN). Either as that list or as whole areas closed on PIN (Admin Hub, Finance,
   Fraud; Statistics without downloads). A "Sign in with your password" button where something is password-only.

## Step 19 — Statistics

1. ~~**See the sales in a currency you know**~~ Built in step 2 (user, 2026-11-19: "do it now"), for the whole app, chosen beside
   the language. Kept here for what was proposed: (user, 2026-11-19: "just to see"; payments always stay in the restaurant's own
   currency, chosen when it was set up).
   Example: a restaurant in the Dominican Republic sells in pesos (DOP); you look at its sales and see "≈ €1,234" or
   "≈ $1,357" beside the pesos.
   Suggestion:
   - Each person picks it for themselves in Profile → Preferences: "Also show amounts in" (None / EUR / USD / …), like the
     language. The Dominican owner keeps None, you pick EUR.
   - Statistics shows the restaurant's amount as it is, with the converted one smaller beside or under it, always with "≈".
     A line says which rate: "≈ at 1 DOP = 0.0149 EUR, 19 Nov".
   - Today's rate for every day shown (simple to read). Each day's own rate would be more exact over a long period, but
     harder to follow.
   - Only on screen: downloaded PDFs and CSVs stay in the restaurant's currency, so reports are never mixed.
   - The server asks Frankfurter once a day and keeps the rate; if there's no rate, nothing extra shows.
   Question: only Statistics, or also My Sales and Salary?
