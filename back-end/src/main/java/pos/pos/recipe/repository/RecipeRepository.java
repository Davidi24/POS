package pos.pos.recipe.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.recipe.entity.Recipe;

import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
}
