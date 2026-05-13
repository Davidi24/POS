/**
 * CURRENT RELATION: recipes.restaurant_id -> restaurants.id
 * CURRENT RELATION: recipes.menu_item_id -> menu-items.id
 * CURRENT RELATION: recipe_components.recipe_id -> recipes.id
 * CURRENT RELATION: recipe_components.inventory_item_id -> inventory_items.id
 * CURRENT RELATION: recipe_components.child_recipe_id -> recipes.id
 *
 * FUTURE RELATION: production batches and prep labels should attach to recipe revisions.
 */
package pos.pos.recipe.entity;
