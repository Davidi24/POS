# 5. Devices & printing

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Devices (kind cards, kind page, device page, add and confirm), Settings → Devices & printing.
- Server: devices, pairing tokens, heartbeats, printers (`RestaurantDeviceRetirement` for archiving).

## How it is now
- Kinds: Tills (`POS_TERMINAL`), Tablets & screens, Printers, Card terminals, Barcode scanners, Cash drawers.
- Adding: pick the kind, editor, then Confirm (pairing code for tills, tablets and terminals).

## Known gaps (MOCK)
- [x] Barcode scanners and cash drawers live only in memory (2026-11-27: cash drawers are devices on the server, plugged into a printer; scanners need nothing set up, a scan anywhere shows what it is).
- [x] The printer test page prints (2026-11-27); a cash drawer opens through its receipt printer on cash payments.
- [x] Nothing printed (2026-11-27: kitchen tickets, bills, receipts, test pages and the drawer print now; the logo and pictures too since the second round).
- [x] Settings → Devices & printing is an empty page (2026-11-27: the restaurant's network, Where things print, When things print).
- [ ] Pictures behind (the designer's backdrops) don't print: a thermal printer can't print words over a picture unless the whole receipt is sent as one picture. **(your call)**

## Order (user, 2026-11-24)
- Tills, tablets and screens first; printers, cash drawers and scanners after.

## Decisions to take
- [x] Tills, tablets and screens only on the restaurant's network (user, 2026-11-24: "do it"): connecting them and PIN
  sign-in on a till work only from the restaurant's network; owners connect from anywhere; the first till saves the network by
  itself and switches the rule on; a switch per branch in Settings → Devices & printing. Built 2026-11-24 (see Done).
- [x] What Settings → Devices & printing holds (user, 2026-11-27): the network rule and "When things print". What a printer prints is chosen on the printer itself (user: "you configure a printer and you say what it should print, that's all"), one printer per till and per station.
- [ ] The till lock (30 s, 3 min in important work) and the PIN pause (5 wrong PINs pause a till 15 min) become settings on
  that page? Suggestion: yes (decide-later step 1 #2; moved here from Settings on 2026-11-24). **(your call)**
- [x] "Connect devices" (user, 2026-11-25: "do it"): its own permission, Owner, Co-Owner and Admin by default, can be given to
  a Manager; connects tills, tablets and screens, on the restaurant's network unless an owner. "Manage devices" stays the
  owner's. Built 2026-11-25 (see Done).
- [x] A till or tablet is connected only by someone signed in on it (user, 2026-11-25: "you don't set up a till from the
  sign-in screen; you must sign in, so I know who set it up"; then "I did not say remove generating the code; the person who
  puts the code logs in and puts it in the Devices section, not the sign-in section"): on the till itself This device → Use
  this device, or a code made on the till's page typed on the new device in Devices → Enter a code by someone signed in there.
  The sign-in screen has no code entry.
- [x] A new code ends the one before (user, 2026-11-25: "should that delete the other? do what you think is best"): only one
  code works per device; the card shows no list of old ones.
- [x] Devices' top says only "This is Till 2" when the app runs on one, nothing otherwise (user, 2026-11-25: the strip "gives
  the idea a device should be a till or a tablet").
- [x] Moving a device to another branch only for someone who works in every branch (user, 2026-11-25: "the owner has only one
  branch, why does it say Change?").
- [x] Who can sign in, "Only some": a bar of role pictures, the chosen role's people with ticks (user, 2026-11-25: "an icon
  for each role, a nav bar; by the role its people ready; admins or down, you know the rule").

## PIN or password (check here)
Rule (user, 2026-11-25): a PIN does the job, a password changes the system (server `security/rbac/PinSignIn.PASSWORD_ONLY`,
a PIN sign-in gets its permissions without those). Now for this step: everything about devices needs the password (connecting, adding, removing, moving, who can sign in, the network rules); a till still signs in by PIN.
- [ ] Going through this step's screens: check each action, PIN or password, and change the list if needed.

## 1. Screens
- [ ] Agree the screens with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- [ ] The feature complete, after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- [ ] Written once development is done.
- The restaurant's network (written 2026-11-24, to add to when the step's list is written):
  - The first till, tablet or screen connected at a branch saves its network and switches the rule on; a card terminal or a
    printer never does; a branch that already has a rule row (even switched off, even with no networks) never learns again.
  - With the rule on: connecting from another network is refused (403, "Connect this device from the restaurant's Wi-Fi.") for
    a code made by an Admin or a Manager given DEVICES_MANAGE, and works for one made by an Owner, a Co-Owner, the Super Admin
    or a support admin (the audit line says "outside the restaurant's network, by an owner"); nothing is learned then.
  - PIN sign-in from another network: OUTSIDE_NETWORK 403, the PIN pad says so, it doesn't count as a wrong PIN or pause the
    till, a password sign-in still works, the refusal is in "Tried from other networks" and in the Audit log ("Unknown person").
  - The address as the server sees it: behind the production proxy (`app.security.trusted-proxies`, X-Forwarded-For), IPv4
    whole, IPv6 by its /64, a private network by its /24, 127.0.0.1 and ::1 the same; a device switching between IPv4 and IPv6
    on one Wi-Fi (both may need saving).
  - Settings → Devices & printing: switch on refused with no network (400), Use this network (only while it isn't saved), at
    most 5 (409 for the sixth), Remove (the last one switches the rule off), Allow this network for a turned-away address from
    any network, but not an address nobody tried from (400); every change in the Audit log ("Restaurant's network").
  - People without DEVICES_MANAGE get 403 on every `/device-network` call; another restaurant's branch is refused.
  - Turned-away tries older than a week disappear; a saved network leaves the list; two first connections at the same moment
    don't break anything (one wins, the other gets a 409 and can retry).
- Connect devices and codes (written 2026-11-25):
  - An Admin (Connect devices, no Manage devices) sees Admin Hub → Devices with only Tills and Tablets & screens, makes a code
    and uses "Use this device as …" for a till, tablet, kitchen screen or guest display; a card terminal's code is 403, as
    are adding, editing, removing, confirming and cancelling codes. A Manager given only Connect devices (no See settings)
    sees the same (no printers loaded). Taking Connect devices off also takes Manage devices (it needs it); V139 gave it to
    everyone who had Manage devices.
  - Codes look like "K7P4-9QXM" (no 0/O, 1/I/L); typed in small letters, without the dash or with spaces they still work;
    an old long code made before still works until it runs out; a used, cancelled or run-out code is "This code doesn't
    work"; a second code for the same device cancels the first.
  - The sign-in screen has no way to connect a device; a till or tablet is connected only by someone signed in on it: This
    device → Use this device, or Devices → Enter a code with a code made on the till's page (the Audit log names who made the
    code and who typed it); someone without Connect devices has no Enter a code (403 on the endpoint); a code of another
    restaurant is "This code doesn't work"; a wrong code is a 400 (the app stays signed in, no second try sent); from another
    network with the rule on, an Admin is refused ("Connect this device from the restaurant's Wi-Fi.") unless an owner made or
    typed the code; the first till of a branch saves the network; a device that was Till 1 and takes a code for Till 2 becomes
    Till 2 (the window says so first), and Till 1's connection ends.
  - 30 tries from one network address in 10 minutes, then 429 "Too many tries…" (`app.devices.pairing.*`); a whole
    restaurant behind one address setting up many devices stays under it. Check the ITs that redeem many codes.
  - `./start.sh app 192.168.x.x` on a second computer: builds and opens the app for that backend, warns when nothing answers.
- Online and this device (written 2026-11-25):
  - A connected till or tablet sends a heartbeat every minute, signed in or on the PIN pad; Devices shows it Online, and
    Offline · "Last seen …" about 3 minutes after the app closes or loses the network (`app.devices.online-minutes`).
  - Connecting the same till on a second device: the first one's heartbeat is refused, its Devices page says "This device was
    …, but it isn't connected any more." with Forget it; its PIN pad says it isn't connected.
  - A till switched to In repair / Switched off / Blocked: "Not in use", its heartbeat refused; back in use and connected
    again, it's online.
  - The kinds page: the "This device" strip (a till, one disconnected, or not one at all); the Tills and Tablets & screens
    cards count online / offline / not connected / not in use. A kind's page groups them under This device, Online,
    Offline, Not connected yet, Not in use; Connect on the ones nobody connected. A device's page: the state in the hero,
    the Connection card first (this device or a code, the code shown big until it's used, the codes made), then its state.
  - The heartbeat sends what the device runs ("Linux 6.x", "Android 14"), shown as System; the address is the device's own
    behind the proxy.
- Who can sign in here, branches, the cards (written 2026-11-25):
  - "Only some": the role bar lists only roles at the editor's level or below (an Admin sees Admin and below, never Owner),
    each with only the people they'd see in Users; Always roles with nobody visible are left out; ticking "Everyone who is a
    …" ticks its people; a person chosen by someone higher, outside the bar, shows under Also chosen and is kept on Save.
  - Everyone (default) or only some roles and people; Owner, Co-Owner, Admin and Manager always can; someone else not chosen
    gets "You can't use this device. Ask a manager." at the PIN pad (NOT_ON_THIS_DEVICE 403, not a wrong PIN, in the Audit
    log); a chosen person who leaves, or a role deleted, drops out by itself; only DEVICES_MANAGE saves (403 otherwise); a
    role or person of another restaurant is a 400; "Only some" with nothing chosen can't be saved.
  - Branch: only with 2+ branches; Change → pick one → "Bar till works at Blloku now." and it leaves this branch's list; its
    connection stays, the new branch's network rule applies at the next connect / PIN.
  - Disconnect asks first; the device cards are small (5 a row on a wide window, 2 on a phone) and say their state.
  - Branch · Change shows only for someone who works in every branch (BRANCHES_ALL or a platform admin) with 2+ branches;
    anyone else gets 403 on the move; the demo `owner` (BRANCHES_ALL denied) never sees it.
  - Removing a till that had codes, was connected, has a kitchen station or clock-ins on it works (204); its shifts and the
    station stay; the Audit log keeps who connected it.
- A second computer (written 2026-11-25): `./start.sh app <this computer's address>` on it, sign in, Devices → Enter a code
  (or the till's page → Use this device), sign out → the PIN pad, PIN sign-in works from the same Wi-Fi; the server's own
  computer and the second one count as one network locally (`loopback-is-this-network`), and in production (behind the
  proxy) every till of the restaurant shows one public address.
- A typed code waits for its maker (written 2026-11-25): typing a till's code on another device shows "Waiting for <maker>"
  with the time left; the maker gets the window (also on another of their devices, and after signing in again within the
  3 minutes) and the notification; Agree connects the new device within a few seconds; Refuse ends the code (typing it again
  fails); no answer in 3 minutes = nothing changes and both sides say so; a second person typing the same code while one
  waits gets "Someone else is waiting"; Cancel on the new device stops it. Agreeing on the device that is that till: it
  leaves the till at once (password sign-in → the start, PIN sign-in → the sign-in screen). The old till on a third device:
  within about 30 seconds "This device isn't Bar till any more", then the same; on its PIN pad it goes to the password
  sign-in. "Use this device" still connects at once (own code only); someone else's code through the redeem endpoint is 400.
- Disconnect first (written 2026-11-25): a till or tablet in use shows no code and no "Use this device", only "In use on
  another device" with Disconnect (asks first); making a code for it through the API is 409; after Disconnect the device that
  had it stops within about 30 seconds ("This device isn't … any more") and a code / Use this device work again; a card
  terminal in use disconnects from its code card; this device becoming another till frees the one it was.
- Devices updates by itself: connecting, disconnecting and a device going on or off show within about 5 seconds on the kinds,
  a kind's page and a device's page, without leaving and coming back; the words are In use · Online, In use · Offline, Not
  connected yet, Switched off.
- Who can sign in (2026-11-27): with "Only some", only owners and co-owners show as Always; an admin or a manager left out
  gets NOT_ON_THIS_DEVICE at PIN sign-in; the device page's cards show only their titles; Devices has more room at the sides
  and the cards clear the right-edge tab.
- 10 wrong PINs (2026-11-27): nine wrong PINs on a till say Wrong PIN, the tenth pauses it 15 minutes (the right PIN waits too);
  the Connection card says "In use on Linux computer (fedora)" with its system and since when, empty again after Disconnect;
  a device connected as a kitchen screen never locks by itself (Lock locks it), and a PIN sign-in there still can't change the
  menu or anything else password-only.
- Printing (written 2026-11-27): a network printer found with "Find printers" and one picked from a computer's printers both
  print the Test page; on a printer's page the tills and kitchen stations chosen get its receipts/bills and tickets, one moved
  from another printer leaves it; sending dishes prints each station's new dishes once on its printer (several sent one after
  another come out together), not again on the next send; Request bill prints the preliminary bill (Settings switch); the last
  payment prints the fiscal receipt (Always / Ask: "Print the receipt?" / Never), cash opens the drawer when the printer has
  one; a device without a receipt printer asks which printer; a printer that's off or unplugged gives a red toast and nothing
  hangs; the KDS card's Print reprints on its station's printer; 58 and 80 mm paper; letters ë ç ñ à € come out right;
  Statistics' Print / Print all ask which of the computer's printers (none: the PDF opens in the viewer).
- Printing, second round (written 2026-11-27): the receipt prints every block as the Receipt designer has it, in order, with
  the logo and pictures as dots at their width (a photo keeps its shades) on 58 and 80 mm; Add a printer offers the six kinds
  and each fills paper, cut and drawer (Document: A4, no cut/drawer, Network or On a computer only); after confirming a new
  receipt or kitchen printer, "What does … print?" opens with the tills and stations; on a printer's page a click takes a till
  or station or lets it go and is saved at once, one shown "on Back printer" moves here and leaves that printer, a document
  printer takes none, a removed printer lets go of what it printed; a printer that's off gives a red toast and nothing hangs;
  a device with no printer of its own asks which one with "Don't print" (one window even with Receipt after payment = Ask);
  a phone/tablet: Bluetooth asks "Nearby devices", lists the paired
  printers, the picked one prints the test page there, and from another till through it (app open on it: prints in a few
  seconds; app closed: after 2 minutes "Is the app open on …?"); a USB printer on one till prints for another till the same
  way; two tills sending to it at once each print once; a document printer on the network prints the A4 test page (IPP, else
  9100) and shows in Statistics' Print choices beside the computer's printers, the last chosen first; a PIN sign-in still can't
  change printers or routes.
- Printers answering (written 2026-11-27): with the app open on a connected till, within a minute every network printer reads
  Online (green) or Offline (red) with "No answer at 192.168.1.52 · checked by Bar till"; switching a printer off turns it
  Offline within about a minute, on again Online; a USB printer is checked only by the till it's plugged into ("isn't on
  this computer any more" when uninstalled), a Bluetooth one by the tablet it's paired with ("Bluetooth is off on this
  device", "isn't paired any more"); with no till open for 3 minutes printers read Not checked; a till that can't reach a
  printer in another branch doesn't turn it Offline while another till reaches it; the Printers card counts the offline ones
  in red; a locked till still checks; nothing comes out of the printers.
- Card terminals, drawers, scanners, codes (written 2026-11-27): a card terminal connected to Bar till by Bluetooth (the paired
  one picked on a tablet, "Paired as" on a computer) or by cable; not connected reads amber everywhere and Bar till's payment
  screen says no terminal; on Bar till a card payment opens "Waiting for Card terminal 1" (Approved takes it, Declined takes
  nothing and says so, Cancel goes back), the tablet without one only records it; a terminal can't be connected to a printer
  or another restaurant's till; removing the till lets go of the terminal. A cash drawer plugged into the receipt printer:
  Open the drawer opens it, a cash payment at a till printing there opens it, a drawer can't go into a till; removing it stops
  the drawer opening. A barcode scanner: a scan with no field clicked shows the item (picture, stock per place) with Open in
  Inventory (Stock's scan window opens there) or New item with this barcode; in a search box it only types; a person without
  stock access sees "You can't see stock items"; signed out nothing happens; Devices → Barcode scanners: Try it reads a scan.
  Codes: Enter a code is only on the Tills and Tablets & screens pages; a till's code in Tablets & screens (and the other
  way) says "This code doesn't work here…" and connects nothing; in its own kind it waits for its maker as before.
- Eight code boxes: typing, pasting "k7p4-9qxm" or "K7P49QXM", Backspace, Enter, the 8th character sends it; a wrong code
  turns the boxes red until typed again; on a phone the keyboard shows capitals and Done.
- A till doesn't lock for 3 minutes on a device's page, in Enter a code and in the agree window.
- [ ] Run (later, when the user says).

## Printing the two receipts (to do; user, 2026-11-23)
- The receipt printers print what Restaurant info → Receipt designs: the preliminary bill and the fiscal invoice (blocks, lines,
  pictures, barcode, QR codes, DGII's QR). The design is kept on the server (`/restaurants/{r}/receipt-design`, since V137).

## Done
- 2026-11-27 — Card terminals through a till or tablet (Bluetooth or cable), cash drawers on the server (plugged into a
  printer), barcode scanners with nothing to set up (a scan anywhere shows what it is), codes typed inside their kind (user:
  "make them work only if they are connected with a till or a tablet"; "the scanner: you just connect it"; "you go inside the
  till to enter the code"). Server V147, `applyLink`, `/card-terminal`, codes by `kind`; app: terminal and drawer fields and
  cards, the payment's terminal step (test mode), `ScanCatcher`/`ScanWindow`/`ScannerPage`, Enter a code on the kind pages.
- 2026-11-27 — Printing, second round (user: "the printer prints exactly as I configured the receipt"; "second only if the
  first fails"; ready-made printer kinds; Bluetooth; a USB printer on one till printing for the others; "a normal printer
  for documents like in Statistics"). Server V145 (`printer_role`, `print_queue`), `/printers/{p}/jobs`, the queue and its
  public host endpoints. App: raster pictures, relay (`PrintRelay`), Bluetooth (Android), printer kinds, What it prints on the
  printer (and right after adding one), document printers (IPP) in Statistics' print choices. A 1st/2nd printer per till and
  a "Where things print" list in Settings were built the same day and taken out again (user: "you configure a printer and you
  say what it should print, that's all"; one printer each).
- 2026-11-25 — Who can sign in on a till or tablet, a device's branch, small cards, separate connect cards (user: "make the
  cards much smaller, ask before disconnect, no branch box with one branch, let me choose the branch; who can use it"). Server
  V140 (`device_allowed_roles`, `device_allowed_users`), `device/signin` (`DeviceSignInService.allows` at PIN sign-in,
  `GET/PUT /restaurants/{r}/devices/{d}/sign-in`), `PATCH …/branches/{b}/devices/{d}/branch {branchId}`. App: `SignInCard`,
  `BranchCard`, `ThisDeviceCard` + `CodeCard` (separate), Disconnect confirm, small `DeviceCard`, the PIN pad's message; the
  "Who uses it / Give to this branch" box is gone (nothing used those assignments).
- 2026-11-25 — Devices made clear (user: "the current device, the other devices connected, online, what is not; make the
  screens much better"). Server: device answers carry `connected`/`connectedAt` and `online` = heard from in the last 3
  minutes (printers keep their own); the heartbeat reads the real address and takes `{platform, osVersion}`. App:
  `core/session/TillHeartbeat` (every minute from `App.kt`, its own client, `state` OK / REFUSED / UNREACHABLE),
  `admin/devices/ui/DeviceConnection.kt` (`Link` Online / Offline / Not connected yet / Not in use, `LinkPill`, `LinkDot`,
  `HereBadge`, `ThisDeviceStrip`, `ConnectCard`), the kinds page, the kind page's groups and rows, the device page.
- 2026-11-25 — "Connect devices" and connecting with a code (user: "connect with the code, both … do it"). Server:
  `DEVICES_CONNECT` (AppPermission, PermissionTree: Devices = Connect devices, then Manage devices under it; Admin role; V139
  gives it to whoever had Manage devices), pairing codes made and devices/codes/assignments read with it (a till, tablet or
  screen only: `DeviceManagementService.createPairingToken`), short codes `PairingCodes` (8 characters, "K7P4-9QXM", typed
  in any case), `PublicDevicePairingController` limits tries per address. App: `auth/ui/login/ConnectWithCodeScreen` from the
  sign-in screen's "Setting up a till? Connect with a code", `DevicesState.canConnect/mayConnect` (Devices place with
  Connect devices; only Tills and Tablets & screens without Manage devices), the code texts point to it. `./start.sh app
  <address>` opens the app for a backend on another computer.
- 2026-11-24 — Tills, tablets and screens only on the restaurant's network (user: "can I make them configured only in the
  restaurant's network? … do it"). Server `pos.pos.device.network` (V138: `device_network_rules`, `device_networks`,
  `device_network_refusals`): `NetworkAddress` (IPv4 whole, IPv6 /64, private /24, loopback = 127.0.0.1),
  `DeviceNetworkService.beforeConnect` (pairing redeem: first device learns the network and switches on; outside refused unless
  the code's maker is an Owner, Co-Owner or platform admin), `beforePin` (PIN sign-in: OUTSIDE_NETWORK 403), refusals kept a
  week; `GET/PUT /restaurants/{r}/branches/{b}/device-network`, `POST …/networks {address?}`, `DELETE …/networks/{id}`
  (DEVICES_MANAGE); audit lines. The pairing endpoint now reads the address through `ClientInfoExtractor` (it used the proxy's).
  App: Settings → Devices & printing (`admin/settings/DeviceNetworkSettings.kt`, `admin/devices/DeviceNetwork.kt`), the PIN
  pad's message, "This Wi-Fi is saved as the restaurant's network" after connecting the first till, the Audit log's area.
