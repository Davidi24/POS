# 11. Menu & recipes

Status: Not started (Not started → Screens → Development → Hard testing list ready → Done)

## Where it is
- App: POS → Menu (managers edit; waiters see what's available now), KDS → Menu, the dish editor's Recipe / Ingredients, Inventory → Items → sub-recipes.
- Server: `/menus`, sections, items, options, photos, `/menus/available-now`, `/menus/waiter-view`, `/menus/order`, dish recipe, `/sub-recipes`.

## How it is now
- Special menus: event menus (linked to an event night) and booking-extras menus.
- Dish photos in R2 (production) or local `uploads/`.
- A dish's ingredients are its one active recipe; selling takes stock by the newest active recipe.

## Known gaps
- Demo dishes have no recipes on purpose: once a dish has one, serving needs a sale stock place and enough stock, or the kitchen gets a 409.

## 1. Screens
- To agree with the user when this step starts (mock data where the server isn't ready).

## 2. Development (the feature complete, no tests)
- Starts after the user approves the screens.

## 3. Hard testing list (run later, when the user says)
- To write once development is done.

## Done
- Nothing yet.
