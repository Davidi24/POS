# 2. Users & roles

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: Admin Hub → Users (role cards, a role's people, Permissions windows, New role, person editor, a person's own permissions).
- Server: `/users`, `/users/{id}/permissions`, `/roles`, `/roles/{id}/permissions`, `/roles/{id}/status`, `/roles/{id}/pay`, `/co-super-admins/{userId}/permissions`, `/auth/register`.

## How it is now
- Who sees and manages whom: people ranked below you, plus people at your level you added yourself; never above you (`RoleHierarchyService`, `assertCanManageUser`).
- Built-in roles from high to low: Super Admin 60000, Co-Super-Admin 55000, Owner 50000, Co-Owner 40000, Admin 30000, Manager 20000, Waiter 10000, Viewer 7500, Kitchen 5000, Cleaner 4000, Extra worker 3000. A restaurant's own role gets the rank just under its maker.
- Built-in roles are shared; a restaurant's changes to them are stored in `restaurant_role_permissions`. A person's own changes are grants and denials.
- Roles can be switched off per restaurant and marked paid or not (`role_pay_settings`).
- Nothing about the Super Admin shows inside a restaurant.
- Staff search forgives accents, typos and partial names (`StaffSearch`).

## Known gaps
- The Kitchen role isn't assignable (`is_assignable=false`), so an owner can't add kitchen staff (403).
- Role seeding only adds permissions; taking one off a built-in role needs a migration.

## Decisions to take
- Who may add kitchen staff.
- What happens to a removed person's shifts, orders and sessions.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
