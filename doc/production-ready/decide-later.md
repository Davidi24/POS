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
