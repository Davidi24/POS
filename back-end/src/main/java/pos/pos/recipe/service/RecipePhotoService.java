package pos.pos.recipe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.exception.recipe.RecipeNotFoundException;
import pos.pos.inventory.service.InventoryChangeNotifier;
import pos.pos.menu.image.MenuImageReleaser;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.menu.image.MenuImageUploads;
import pos.pos.recipe.dto.RecipeResponse;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.mapper.RecipeMapper;
import pos.pos.recipe.repository.RecipeRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.UUID;

/** Stores the photo for a house-made inventory preparation using the shared image store. */
@Service
@RequiredArgsConstructor
public class RecipePhotoService {

    private final RestaurantScopeService restaurantScopeService;
    private final RecipeRepository recipeRepository;
    private final RecipeMapper recipeMapper;
    private final MenuImageStorage imageStorage;
    private final MenuImageReleaser imageReleaser;
    private final InventoryChangeNotifier inventoryChangeNotifier;

    @Transactional
    public RecipeResponse upload(Authentication authentication, UUID restaurantId, UUID recipeId, MultipartFile file) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Recipe recipe = requireRecipe(restaurantId, recipeId);
        MenuImageUploads.CheckedPhoto photo = MenuImageUploads.check(file);
        String key = imageStorage.store(restaurantId, photo.content(), photo.contentType(), photo.extension());
        imageReleaser.discardOnRollback(key);
        return replace(recipe, restaurantScopeService.currentUserId(authentication), imageStorage.urlFor(key));
    }

    @Transactional
    public RecipeResponse remove(Authentication authentication, UUID restaurantId, UUID recipeId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        return replace(requireRecipe(restaurantId, recipeId), restaurantScopeService.currentUserId(authentication), null);
    }

    private RecipeResponse replace(Recipe recipe, UUID actorId, String url) {
        String oldUrl = recipe.getImageUrl();
        recipe.setImageUrl(url);
        recipe.setUpdatedBy(actorId);
        Recipe saved = recipeRepository.saveAndFlush(recipe);
        if (oldUrl != null && !oldUrl.equals(url)) imageReleaser.releaseAfterCommit(oldUrl);
        inventoryChangeNotifier.changedAfterCommit(recipe.getRestaurant().getId());
        return recipeMapper.toResponse(saved);
    }

    private Recipe requireRecipe(UUID restaurantId, UUID recipeId) {
        return recipeRepository.findByIdAndRestaurant_Id(recipeId, restaurantId).orElseThrow(RecipeNotFoundException::new);
    }
}
