# 1. Sign-in & accounts

Status: Development (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Login, Forgot password, Set a password from a link (only from Forgot password → "I have the link" since 2026-11-17; the emailed link works in the browser), Open a restaurant / Check an application, the workspace picker, the profile menu (sign out).
- Server: `POST /auth/device/login`, refresh, `POST /auth/forgot-password`, `POST /auth/password-link`, `POST /auth/reset-password`, the password page `/public/account/password?token=`, `POST /restaurants/registrations` and `/registrations/status`.

## How it is now
- A person keeps at most 3 sessions (`max-active-sessions`); a new login signs out the oldest one.
- Permissions come from `EffectivePermissions` (roles as the restaurant has them, minus denials, plus grants) at login, refresh and on every request.
- Password links: INVITE (72 h) and RESET (30 min); a new link replaces older ones; states VALID / EXPIRED / USED / REPLACED / UNAVAILABLE / INVALID.
- Password rules: 8+ characters, an uppercase letter, a number and a special character (`PasswordRules`), the same on the page and in the app.
- Account emails go through the email outbox (retries 1, 3, 9, 27, 81 min, then every 2 h, 8 tries).
- Staff of a suspended, closed or removed restaurant can't sign in, and their sessions get 401 at once.
- Public applications: on by default, rate limited, no CAPTCHA, a private 20-character code to check the status.

## Known gaps
- The phone app's server address is fixed to this computer (`192.168.178.22:8080` in `platformBaseUrl.kt`).
- Locally, emails only reach MailHog; production needs a real email server and `PUBLIC_BASE_URL` / `PASSWORD_LINK_BASE_URL`.
- Scripts that log in as `admin` again and again sign the user's app out (3 sessions).

## Decisions to take
- Session length and the 3-session limit.
- Lock an account after many wrong passwords?
- A CAPTCHA on public applications?

## 1. Screens
One screen at a time (user, 2026-11-16, after a first try that redid every screen's form: "the right part looks great but the
left part looks terrible; login was perfect, just the fonts; one screen at a time, login first").

Every sign-in form is at most 420 dp wide (user, 2026-11-16: "same max width for all forms").

Every sign-in screen (2026-11-17, user: "remove the house; the bottom-left thing not in login only, in all pages"; "remove the logo in all"; "a normal back arrow, the curved one is only inside"): no house (screens list), no logo, a plain back arrow, the sign-in form's fonts and text sizes (2026-11-17, "make all the other pages the same as login": Inter; titles Bold 27 sp, labels SemiBold 14, fields 14.5, buttons SemiBold), and the small home-screen corner bottom left (`AuthImageScaffold`, and `PublicPage` for Open a restaurant / Check an application).

Kept for every sign-in screen: the photo panel on the right (`AuthImageScaffold`, from 960 dp, form 45% / photo 55%: rounded, dark fade, "Every table,
order and booking in one place.").

| Screen | Status |
|---|---|
| Sign in (password) | Fonts changed to Inter, layout as it was; form 420 dp; no logo for now (2026-11-17). Liked by the user. |
| Sign in with PIN | Not started (as it was) |
| Forgot password | Not started (as it was) |
| Set a password from a link | Not started (as it was) |
| Password changed | Not started (as it was) |
| Create a PIN | Not started (as it was) |
| Account locked | Not started (as it was) |
| Signed out | Not started (as it was) |
| Open a restaurant / Check an application | Out of the app (2026-11-17): sign in has "Don't have an account? Click here", which opens the web page where a new restaurant applies (`SignUpPage.url()`, `<backend>/public/apply` for now). The web page isn't built yet; the app's form and status screens stay in the code, unused. |

Known MOCKs in these screens (for development): PIN sign-in isn't checked (no PIN sign-in on the server; `users.pin_hash`
exists), Create a PIN doesn't save, Account locked is a sample (the server does lock an account for 15 minutes after 5 wrong passwords, but answers with the same "wrong username or password" message, so the app can't tell), nothing leads to Signed out or
Account locked, and the small house (screens list) must come out before going live.

### Pictures needed (the user makes them)
Only the icons that stand on a screen as its picture (user, 2026-11-16: "like PIN, locked…, not the small ones in the inputs").
Square PNG about 1024 × 1024, transparent background, in `composeResources/drawable/`. Already there: `brand_logo`,
`auth_login_img`.

Received 2026-11-16 (the user's files 1–9 from the repo root; trimmed to 512 × 512 in `drawable/`, originals in
`~/Pictures/POS app pictures/auth/`):
| File | Shows | For |
|------|-------|-----|
| `auth_pin` | shield with a padlock over a PIN field | Sign in with a PIN |
| `auth_email_sent` | open envelope with @ and a green tick | Check your email |
| `auth_account_locked` | green shield, person, red padlock | Account locked |
| `auth_signed_out` | window, person, arrow going in | Signed out ("Sign in again") |
| `auth_application_sent` | form with a paper plane and a tick | Application sent |
| `auth_account_off` | window, person, red padlock | Account switched off |
| `auth_pin_create` | window, padlock, PIN boxes and a plus | Create a PIN |
| `auth_forgot_password` | window, padlock with a question mark, password field | Forgot password |
| `auth_link_expired` | window, envelope with a link, red hourglass | The email link expired or was used |

Still to make: `auth_password_done` (password set / changed), `auth_application_waiting`, `auth_application_approved`,
`auth_application_rejected`, `auth_no_connection`, `auth_restaurant_closed`.

## 2. Development (the feature complete, no tests)
User, 2026-11-16: "make it functional first with everything; the remaining screens' look at the end".

### To do
Items marked **(your call)** wait for the user's decision.

**Sign in with a password**
- [x] A clear message for each case: wrong username or password, account switched off, restaurant closed or suspended, too many tries, no connection (replaces the technical "Cannot reach the backend…" text). Done 2026-11-16.
- [x] Locked account: the app shows Account locked with the real time left (decided: the middle way, see Decisions). Done 2026-11-16.
- [x] Sign in stays off (grey) until both fields are filled; Enter in the password signs in. Done 2026-11-16.
- [x] Tab moves from the username to the password (Shift+Tab back); Enter signs in from either field once both are filled (Enter in the username with no password moves to the password; on phones the keyboard's Next / Done do the same). Done 2026-11-17.
- [x] Accounts with an email that was never confirmed can't sign in (user, 2026-11-17: "yes, unconfirmed emails can not log in"). Staff added by a manager get a confirmation email when they're added (`AuthRegisterService` → `issueVerificationForUser`); until they use it, sign in answers "Your email isn't confirmed yet. Use the link in the email we sent you, or ask your manager." (EMAIL_NOT_CONFIRMED, only after the right password). No change needed.

**Sign-in limits (agreed and built 2026-11-17)**
- [x] Count only wrong tries per device (50 in 15 min, the app sends a device id) and per network address as a safety net (200 in 15 min); right sign-ins never count. Signed-in people are never affected.
- [x] Every block lasts 15 minutes from the moment it starts; the server sends when it ends and the app counts down.
- [x] Short messages (user: "not long, make it small, make it look good"):
  - Username locked: "Account locked" · "Too many wrong passwords. Try again in 14:52." · [Back to sign in], with a "Forgot password?" link under it while the lock runs (a new password unlocks it now; 2026-11-17).
  - Device: "Paused on this device" · "Too many wrong tries. Try again in 14:52."
  - Network: "Paused on this network" · "Many wrong tries on this Wi-Fi. Try again in 14:52." · "Signed in? Keep working. Or use mobile data."
  - Warning after this device's 3rd wrong try in a row (no number, so it tells nothing): the wrong-password card's second line "Careful: more wrong tries pause sign-in.", and the "Forgot password?" link under the password turns dark and bold (one link, not two; 2026-11-17). Passwords under 8 characters never count (the server turns them away before counting).

**Staying signed in and signing out**
- [ ] How long a sign-in lasts (today 1 day, so everyone signs in again every day). **(your call)**: e.g. 30 days on your own device, one shift on shared tills.
- [ ] When the server signs someone out (signed in too long, password changed, signed out from another device, too many devices, restaurant closed), show the Signed out screen with the reason.
- [ ] Sign out really tells the server (today most places only forget it on the device, so the session stays open and counts toward the 3-device limit).
- [ ] "Sign out everywhere" in the profile menu.
- [ ] The 3-device limit. **(your call)**: what number (tills, tablets, a phone); tell the person when their oldest device got signed out.
- [ ] Opening the app when already signed in: a short loading look, no flash of the sign-in screen.

**Passwords**
- [x] Daily limits (user, 2026-11-17): "Forgot password?" sends a person at most 7 links a day (10 at first, lowered the same day); a signed-in person changes their password at most 30 times a day ("so I know, but it's suspicious"). Past either one they get one email that day saying the limit was reached; the Forgot password screen answers the same either way. Done 2026-11-17.
- [x] One network address gets at most 30 "Forgot password?" requests an hour, for any email (known or not), so nobody can flood many people's inboxes from one place; past it nothing is sent and the screen answers the same. Done 2026-11-17.
- [x] A new password must differ from the current one (change and reset links; not a first password from an invitation). Done 2026-11-17.
- [ ] Forgot password also accepts the username, not only the email.
- [ ] Change my password while signed in (profile menu), then "you were signed out on your other devices".
- [ ] The link in the email opens the app on "Set your password", not only the browser. **(your call)**
- [ ] Emails go through a real email server in production.

**PIN**
- [ ] **(your call)** The rules: 4–6 digits, different for each person in the restaurant, only on the restaurant's paired tills and tablets, locked after a few wrong tries.
- [ ] Server: set / change / remove a PIN, sign in with a PIN on a paired device, count wrong PINs and lock.
- [ ] App: Create a PIN really saves it (from the profile menu, and offered after the first sign-in on a till); Sign in with a PIN really checks it and shows the device's real name; the PIN option is hidden on devices that aren't paired.
- [ ] A manager can reset someone's PIN (together with step 2, Users).

**My account (profile menu)**
- [ ] Name, role and restaurant; Change password; My PIN; My devices (see and end sessions); Sign out; Sign out everywhere.

**Phone app**
- [ ] The server address isn't fixed to this computer: each build gets the real address, with a hidden way to point at a test server.

**Safety and records**
- [ ] Audit log entries for: signing in, failed sign-ins, locks, password reset and change, PIN set or reset, sign out everywhere, a manager ending someone's session.
- [ ] Tell the owner or manager when an account gets locked. **(your call)**
- [ ] Decide which of these a restaurant can change in Admin Hub → Settings (wrong-try limit, lock minutes, how long a sign-in lasts, PIN length). **(your call)**
- [x] Remove the little house (screens list) from every sign-in screen (done 2026-11-17; Create a PIN's back now goes back or to sign in, not to the screens list).

**Open a restaurant / Check an application**
- [x] Out of the app: "Don't have an account? Click here" on sign in opens the web (2026-11-17).
- [ ] The web page itself: the application form and the status check by code, on the web (where: a page the backend serves like the password page, or the website). **(your call)**
- [ ] Already finished in October; only check its emails reach the real email server. **(your call)**: add a "not a robot" check?

### Decisions (fill in as the user answers)
- 2026-11-17: no "Got an invitation?" on sign in; the link in the email opens the password page in the browser, and Forgot password → "I have the link" still opens Set your password in the app. Sign in now only has Sign in, Forgot password?, PIN and Don't have an account?
- 2026-11-17, after "a hacker who knows an email can send disturbing emails all the time?": 7 links a day per person (not 10, not 5), so at most 8 emails a day to one person (7 links + one "limit reached"); and at most 30 "Forgot password?" requests an hour from one network address.
- 2026-11-17, "make 8 wrong before locking the account": a username locks after 8 wrong passwords within 15 minutes (was 5), for 15 minutes from the 8th; `app.security.login.max-failed-attempts: 8`. The warning still shows after the 3rd wrong try in a row.
- 2026-11-17, password limits: change while signed in at most 30 a day, "Forgot password?" at most 10 links a day, both over the last 24 hours; past either one the person gets one email that day ("you've reached your limit"), and the forgot screen's answer stays the same so it tells nothing. Account locked: "Back to sign in" is the main button, "Forgot password?" a link under it.
- 2026-11-16, "so the hacker now knows the username": a lock must tell nothing either. One rule for every username, real or not: 5 wrong passwords within 15 minutes stop that username for 15 minutes from the last of them, counted from `auth_login_attempts` (wrong passwords only; tries while locked don't count; after the person's last sign-in or new password). A made-up username locks exactly like a real one, so "locked" no longer gives anything away.
- 2026-11-16, sign-in messages ("go, follow your advice, add animation"): the middle way. Wrong username or password = one answer, never says which; switched off, email not confirmed and restaurant closed are told only after the right password; a lock is shown with until when (before the per-account try limit); too many tries from one device is its own answer; no connection is a plain message. No "tries left" counter (it would tell the account exists).

### Done in development
- 2026-11-16 Sign-in messages. Server: `LoginRefusedException` (reason WRONG_CREDENTIALS 401, ACCOUNT_LOCKED 423 + `lockedUntil`, ACCOUNT_OFF / EMAIL_NOT_CONFIRMED / RESTAURANT_CLOSED 403, TOO_MANY_TRIES 429), answered as `LoginErrorResponse {status, message, code, lockedUntil}`; `AuthLoginService.login` order: IP limit → locked → account limit → unknown user → wrong password (the one that locks says locked) → only then switched off / email / restaurant closed; it's in `noRollbackFor` so attempts are still counted. App: `ApiErrorResponse`/`ApiException` carry `code` and `lockedUntil`; `LoginScreenModel` maps them to `LoginProblem(kind, message, lockedUntil)` (400/401 = wrong); the sign-in screen shakes the two fields on a wrong password, slides a tinted card in above Sign in (the switched-off one with `auth_account_off`, no connection with Try again), clears it when typing, greys Sign in until both fields are filled, signs in on Enter, and opens `AccountLockedScreen(until)`: the user's picture pops in and floats, a ring empties with the real countdown, "Choose a new password" (a reset unlocks at once), then "You can try again now". Checked live: unknown user and wrong passwords answer WRONG_CREDENTIALS, the 5th answers ACCOUNT_LOCKED with the time, the right password while locked stays locked.

- 2026-11-16 Lock rule that tells nothing: `AuthLoginService.lockedUntil(user, identifier, now)` over `AuthLoginAttemptRepository.wrongPasswordTimesForUser|ForUnknown` (5 within 15 min → until the last + 15 min; before the per-account try limit, so a made-up username no longer answers "too many tries" where a real one answers "locked"). `users.locked_until` is still written for display; the old counter-based lock is gone (a reset unlocks through `password_updated_at`).

- 2026-11-17 Sign-in limits. Server: V119 adds `auth_login_attempts.device_id` (+ index) and the reason DEVICE_RATE_LIMITED; `ClientInfo` gets `deviceId` (`AppAuthController` reads the `X-Device-Id` header; `ClientInfoNormalizer.normalizeDeviceId` keeps `[A-Za-z0-9_-]{1,100}`); `AuthLoginService`: network paused (200 wrong passwords from the address within 15 min) → device paused (50 on the device) → username locked (5) are checked first, each "15 minutes from the wrong try that started it" (`pausedUntil` over `wrongPasswordTimesForDevice|ForAddress`), and the wrong try that starts one answers it at once; the old "20 tries of any kind per IP in 5 min" and the per-account limit are gone; right sign-ins never count; answers DEVICE_PAUSED / NETWORK_PAUSED (429) with `lockedUntil`; settings `app.security.login.max-wrong-per-device|max-wrong-per-address`. App: `core/session/DeviceIdentity` (expect/actual: `~/.saporini/device-id.txt`, Android SharedPreferences) sent as `X-Device-Id` on sign-in; `LoginProblemKind.DEVICE_PAUSED|NETWORK_PAUSED`; the problem card is now a bold title + one short line (pauses count down with `rememberSecondsLeft`; the network one adds "Signed in? Keep working. Or use mobile data."); `PauseWarning` after the 3rd wrong password in a row (`LoginUiState.wrongInARow`, reset by a sign-in); Account locked texts shortened. Checked live: with one device id, wrong tries 1–50 over 11 made-up usernames (each locking after 5), the 51st answers DEVICE_PAUSED until the 50th + 15 min; another device id on the same address and a request without one still answer normally; test rows deleted.

- 2026-11-17 Password limits. Server: V120 `auth_password_events (user_id, kind RESET_LINK_SENT | RESET_LIMIT_EMAIL | CHANGED | CHANGE_LIMIT_EMAIL, happened_at)`, cleaned up with the sign-in attempts (90 days); `auth/service/PasswordDailyLimits` (`maySendResetLink`/`resetLinkSent`, `checkChange`/`changed`; settings `app.security.password.max-changes-per-day` 30, `max-reset-links-per-day` 10); `PasswordResetService.requestEmailReset`: after the 2-minute wait, the limit, then the link (or a fresh invitation) and it's counted; past the limit nothing is sent and `AuthMailService.sendResetLinkLimitEmail` goes once a day ("No more password links today"); `ChangePasswordService.changePassword`: past 30 a 429 "Your password was changed 30 times today. Try again tomorrow." with `sendPasswordChangeLimitEmail` once a day (`noRollbackFor`, so the email stays), then the current password, `PasswordRules`, and "Choose a password different from your current one." (400; also on reset links and SMS codes; the link stays usable); email kind `PASSWORD_LIMIT`. App: Account locked's main button is Back to sign in, "Forgot password?" under it.

### Notes for development (what the server already has)
- `/auth/device/login|refresh|logout|logout-all`, `/auth/me`, `/auth/forgot-password`, `/auth/password-link`, `/auth/reset-password` (+ `/code` by SMS), `PUT /auth/change-password` (keeps this session, ends the others), `/auth/sessions` (list, end one, end the others), `/users/{id}/sessions` (a manager's view), email and phone verification.
- Login attempts stored in `auth_login_attempts`; lock after 5 wrong passwords for 15 minutes (`app.security.login.*`), 20 tries per IP and 5 per account in 5 minutes; the answer is always the generic "invalid credentials" message.
- At most 3 sessions (`max-active-sessions`), access token 15 min, refresh 1 day.
- `users.pin_hash`, `pin_enabled`, `pin_attempts` exist; no PIN endpoints.
- App: most workspaces' Sign out call `SessionManager.signOut()` only; `WorkspaceScreenModel.logout` is the one that calls `/auth/device/logout`.

## 3. Hard testing list (run later, when the user says)
- Desktop keys on sign in: Tab from the username lands in the password, Shift+Tab goes back; Enter in either field with both filled signs in once (no double sign-in); Enter with the password empty moves to it; Enter while signing in does nothing. Phone: Next on the username keyboard goes to the password, Done signs in.
- Forgot password per address: 30 requests in an hour from one address (mixed real, unknown and made-up emails) → the 31st and later send nothing and still answer 204; another address still works; after the hour it works again; behind the proxy the real address is counted (`trusted-proxies`). Update `PasswordControllerTest` (the controller now calls `requestReset(request, ip)`).
- Forgot password: 7 links in 24 h (2 min apart) → the 8th sends nothing and one "No more password links today" email; the 9th sends neither; the answer is the same as for an unknown email; after 24 h from the first it works again; an owner's fresh invitation from Forgot password counts too.
- Change password: 30 changes in 24 h → the 31st answers 429 and sends one "changed many times today" email (the refusal must not roll it back); the 32nd sends no second email; the same password as now → 400 (change, reset link, SMS code) and the link still works afterwards; a weak new password → the `PasswordRules` message.
- Device pause: 50 wrong passwords from one device id → DEVICE_PAUSED for 15 min from the 50th; other device ids and no id unaffected; right sign-ins never count; tries while paused don't extend it.
- Network pause: 200 wrong passwords from one address → NETWORK_PAUSED; people already signed in keep refreshing.
- App: the warning appears after the 3rd wrong password in a row and goes after a sign-in; pause cards count down and turn to "You can try again now".
- Sign-in answers: unknown user and real user give the same answers at every step (×1–7 WRONG_CREDENTIALS, 8th ACCOUNT_LOCKED with the time, then locked for 15 minutes, then free); wrong password ×1–7 (WRONG_CREDENTIALS, same text and time), 8th (ACCOUNT_LOCKED + time), right password while locked (locked), after the lock ends (signs in, counters reset), switched off / email not confirmed / restaurant closed only with the right password, 20 tries from one IP (TOO_MANY_TRIES), a password reset unlocks.
- Update the old auth tests that expect "Invalid username/email or password", "Too many login attempts. Try again later." or `InvalidCredentialsException` from login (e.g. `WebAuthIntegrationTest`, `AuthLoginServiceTest`).
- App: each `LoginProblemKind` from a fake server answer; Account locked counts down from `lockedUntil` and turns to "You can try again now".

## Done
- Nothing yet.
