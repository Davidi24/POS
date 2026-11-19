# Decide later

Things suggested during the production pass that are still waiting for your decision (user, 2026-11-18: "I'll see them at
the end"). Each one has what it is, the suggestion, and the step it belongs to. Nothing here is built until you decide.

## Step 1 — Sign-in & accounts

1. **Tell the owner or manager when an account gets locked.**
   When someone's account locks after 8 wrong passwords, the chosen roles get a notification (the bell and a yellow pop-up,
   like unpaid orders). It could mean someone is guessing that person's password, or that the person forgot it and needs help.
   Suggestion: yes, with "Who is told" in Admin Hub → Settings (Owner and Manager by default).
   Also to decide: should a till that pauses after 5 wrong PINs tell them too?

2. **Which limits a restaurant can change in Admin Hub → Settings.**
   Today they're fixed: 8 wrong passwords lock an account for 15 minutes; a sign-in lasts 30 days without use; 5 devices per
   person; a PIN has 5 digits; 5 wrong PINs pause a till for 15 minutes; a till locks after 30 seconds (3 minutes in
   important work).
   Suggestion: the till lock times and the PIN pause become settings (they depend on how a restaurant works); the password
   and sign-in limits stay fixed for everyone (they're about safety, not about how a restaurant works).

3. **A Manager connecting tills.**
   Today connecting a till needs "change settings" (Admin and up). An owner can give that to a Manager, but it gives them
   all of Settings.
   Suggestion: a separate "Connect tills" permission the owner can tick for a Manager, without the rest of Settings.

4. **Typing a pairing code on a new till.**
   Today a till is connected from Admin Hub → Devices while signed in on it. The other way: a manager makes a code on any
   device and types it on the new till's sign-in screen (a small "Set up this device as a till" link).
   Suggestion: add it when the first real restaurant sets up its tills.

5. **The hidden server box in the phone app** (with the server address, waiting for the domain).
   A secret spot in the app to change which server it talks to, for trying a new version on a test server.
   Question: only in the test copies you get, or also in the app restaurants install? If restaurants have it, a curious
   waiter could type a wrong address and the till would stop working.
   Suggestion: only in test copies.

6. **A "not a robot" check on the restaurant application** (now a web page, step 23).
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
   Suggestion: when the web form is built (step 23), it sends the page's language and the emails follow it.

## Step 4 — Restaurant & branches

1. **Today's exchange rate in the currency change.**
   Today the person changing a restaurant's currency types the rate themselves. Frankfurter (`api.frankfurter.dev/v2/rates`,
   free, no key; it has the lek and the Dominican peso, among others) could fill it in: "Today's rate: 1 EUR = 90.29 ALL ·
   19 Nov".
   Suggestion: the server asks for it (once a day, kept), the app shows it filled in and the person can still change it. If the
   service doesn't answer, the field stays empty and they type it, as today.

## Step 20 — Statistics

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
