package pos.pos.recipe.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.enums.RecipeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    Optional<Recipe> findByIdAndRestaurant_Id(UUID id, UUID restaurantId);

    List<Recipe> findAllByRestaurant_IdOrderByNameAsc(UUID restaurantId);

    List<Recipe> findAllByRestaurant_IdAndRecipeTypeOrderByNameAsc(UUID restaurantId, RecipeType recipeType);

    List<Recipe> findAllByRestaurant_IdAndStatusOrderByNameAsc(UUID restaurantId, RecipeStatus status);

    Optional<Recipe> findByRestaurant_IdAndMenuItem_IdAndStatus(UUID restaurantId, UUID menuItemId, RecipeStatus status);

    Optional<Recipe> findByRestaurant_IdAndCode(UUID restaurantId, String code);

    // A dish has one ACTIVE recipe; should older data hold more, the newest version counts.
    Optional<Recipe> findFirstByRestaurant_IdAndMenuItem_IdAndStatusOrderByVersionDesc(UUID restaurantId, UUID menuItemId, RecipeStatus status);

    List<Recipe> findAllByRestaurant_IdAndMenuItem_IdAndStatusOrderByVersionDesc(UUID restaurantId, UUID menuItemId, RecipeStatus status);

    List<Recipe> findAllByMenuItem_IdIn(java.util.Collection<UUID> menuItemIds);

    @Query("select coalesce(max(r.version), 0) from Recipe r where r.menuItem.id = :menuItemId")
    int maxVersionForMenuItem(@Param("menuItemId") UUID menuItemId);

    // Shared preparations (sauce, dough) that dishes can use as an ingredient.
    List<Recipe> findAllByRestaurant_IdAndStatusAndRecipeTypeInOrderByNameAsc(
            UUID restaurantId, RecipeStatus status, java.util.Collection<RecipeType> recipeTypes);

    // The sub-recipe that makes [itemId], if the item is made here.
    Optional<Recipe> findByOutputItem_Id(UUID itemId);

    // Which items of the restaurant are made here, and by which sub-recipe: [item id, recipe id] pairs.
    @Query("select r.outputItem.id, r.id from Recipe r where r.restaurant.id = :restaurantId and r.outputItem is not null")
    List<Object[]> outputItemLinks(@Param("restaurantId") UUID restaurantId);

    // Recipes outside [recipeIds] that use one of them as an ingredient.
    // Recipes still in use (not archived) that take this stock item, or this sub-recipe, as an ingredient.
    @Query("""
            select distinct c.recipe.name from RecipeComponent c
            where c.inventoryItem.id = :itemId and c.recipe.status <> pos.pos.recipe.enums.RecipeStatus.ARCHIVED
            order by c.recipe.name
            """)
    List<String> namesOfLiveRecipesUsingItem(@Param("itemId") UUID itemId);

    @Query("""
            select distinct c.recipe.name from RecipeComponent c
            where c.childRecipe.id = :recipeId and c.recipe.id <> :recipeId and c.recipe.status <> pos.pos.recipe.enums.RecipeStatus.ARCHIVED
            order by c.recipe.name
            """)
    List<String> namesOfLiveRecipesUsingRecipe(@Param("recipeId") UUID recipeId);

    @Query("select distinct c.recipe.name from RecipeComponent c where c.childRecipe.id in :recipeIds and c.recipe.id not in :recipeIds")
    List<String> namesOfRecipesUsing(@Param("recipeIds") java.util.Collection<UUID> recipeIds);
}
