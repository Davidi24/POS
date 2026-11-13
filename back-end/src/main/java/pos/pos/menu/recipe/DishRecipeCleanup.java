package pos.pos.menu.recipe;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.repository.RecipeRepository;
import pos.pos.menu.image.MenuImageReleaser;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Removes dishes' recipes before the dishes themselves are deleted (item, section or whole-menu deletes), so the
 * delete isn't refused by the recipe's link to the dish. A recipe that another recipe uses as an ingredient stops it.
 */
@Component
@RequiredArgsConstructor
public class DishRecipeCleanup {

    private final RecipeRepository recipeRepository;
    private final MenuImageReleaser imageReleaser;

    public void releaseDishes(Collection<UUID> menuItemIds) {
        if (menuItemIds.isEmpty()) {
            return;
        }
        List<Recipe> recipes = recipeRepository.findAllByMenuItem_IdIn(menuItemIds);
        if (recipes.isEmpty()) {
            return;
        }
        List<UUID> recipeIds = recipes.stream().map(Recipe::getId).toList();
        List<String> users = recipeRepository.namesOfRecipesUsing(recipeIds);
        if (!users.isEmpty()) {
            throw new AuthException("This dish's recipe is used inside " + String.join(", ", users)
                    + ". Remove it there first (Admin Hub → Inventory → Preparations).", HttpStatus.CONFLICT);
        }
        recipes.stream().map(Recipe::getImageUrl).filter(java.util.Objects::nonNull).forEach(imageReleaser::releaseAfterCommit);
        recipeRepository.deleteAll(recipes);
        recipeRepository.flush();
    }
}
