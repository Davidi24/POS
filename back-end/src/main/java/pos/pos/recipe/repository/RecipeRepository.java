package pos.pos.recipe.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
