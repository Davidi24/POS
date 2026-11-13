package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuItemDeletionBlockedException;
import pos.pos.exception.menu.MenuItemNotFoundException;
import pos.pos.exception.menu.MenuItemSectionMismatchException;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.exception.menu.MenuSectionMenuMismatchException;
import pos.pos.exception.menu.MenuSectionNotFoundException;
import pos.pos.menu.dto.request.CreateMenuItemRequest;
import pos.pos.menu.dto.response.MenuItemSummaryResponse;
import pos.pos.menu.dto.update.UpdateMenuItemAvailabilityRequest;
import pos.pos.menu.dto.update.UpdateMenuItemRequest;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.image.MenuImageReleaser;
import pos.pos.menu.image.MenuImageStorage;
import pos.pos.menu.image.MenuImageUploads;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuItemService {

    private final MenuRepository menuRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuVariantRepository menuVariantRepository;
    private final MenuItemOptionGroupRepository menuItemOptionGroupRepository;
    private final MenuMapper menuMapper;
    private final ActorScopeService actorScopeService;
    private final MenuPolicy menuPolicy;
    private final RestaurantValidationService restaurantValidationService;
    private final OnlineMenuService onlineMenuService;
    private final MenuImageStorage menuImageStorage;
    private final MenuImageReleaser menuImageReleaser;
    private final pos.pos.menu.recipe.DishRecipeCleanup dishRecipeCleanup;

    @Transactional(readOnly = true)
    public List<MenuItemSummaryResponse> getItems(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            Boolean available,
            boolean includeVariants,
            boolean includeOptionGroups
    ) {
        Menu menu = requireAccessibleMenu(authentication, menuId);
        MenuSection section = requireScopedSection(menu, sectionId);
        List<MenuItem> items = available == null
                ? menuItemRepository.findBySectionIdOrderByDisplayOrderAscNameAsc(section.getId())
                : menuItemRepository.findBySectionIdAndAvailableOrderByDisplayOrderAscNameAsc(section.getId(), available);

        if (items.isEmpty()) {
            return List.of();
        }
        List<UUID> itemIds = items.stream().map(MenuItem::getId).toList();
        Map<UUID, List<MenuVariant>> variantsByItemId = includeVariants
                ? menuVariantRepository.findByMenuItemIdInOrdered(itemIds).stream()
                        .collect(Collectors.groupingBy(variant -> variant.getMenuItem().getId(), Collectors.toList()))
                : Map.of();
        Map<UUID, List<MenuItemOptionGroup>> optionGroupsByItemId = includeOptionGroups
                ? menuItemOptionGroupRepository.findByMenuItemIdInOrdered(itemIds).stream()
                        .collect(Collectors.groupingBy(link -> link.getMenuItem().getId(), Collectors.toList()))
                : Map.of();

        return items.stream()
                .map(item -> menuMapper.toMenuItemResponse(
                        item,
                        includeVariants ? variantsByItemId.getOrDefault(item.getId(), List.of()) : null,
                        includeOptionGroups ? optionGroupsByItemId.getOrDefault(item.getId(), List.of()) : null
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuItemSummaryResponse getItem(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            boolean includeVariants,
            boolean includeOptionGroups
    ) {
        Menu menu = requireAccessibleMenu(authentication, menuId);
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        List<MenuVariant> variants = includeVariants
                ? menuVariantRepository.findByMenuItemIdOrderByDisplayOrderAscNameAsc(item.getId())
                : null;
        List<MenuItemOptionGroup> optionGroups = includeOptionGroups
                ? menuItemOptionGroupRepository.findByMenuItemIdOrdered(item.getId())
                : null;
        return menuMapper.toMenuItemResponse(item, variants, optionGroups);
    }

    @Transactional
    public MenuItemSummaryResponse createItem(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            CreateMenuItemRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);

        MenuItem item = new MenuItem();
        item.setSection(section);
        item.setSku(NormalizationUtils.normalizeUpper(request.getSku()));
        item.setName(NormalizationUtils.normalize(request.getName()));
        item.setDescription(NormalizationUtils.normalize(request.getDescription()));
        item.setBasePrice(request.getBasePrice());
        item.setImageUrl(NormalizationUtils.normalize(request.getImageUrl()));
        item.setAvailable(request.getAvailable() == null || request.getAvailable());
        item.setSendToKitchen(request.getSendToKitchen() == null || request.getSendToKitchen());
        applyOnlinePlacement(menu, item, request.getShowOnline(), request.getOnlineSectionId(), request.getOnlineSectionName());
        item.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        item.setIngredients(request.getIngredients());
        item.setOrderBeforeHours(request.getOrderBeforeHours());
        item.setOccasionCodes(occasionCodes(request.getOccasionCodes()));

        return menuMapper.toMenuItemResponse(menuItemRepository.saveAndFlush(item));
    }

    @Transactional
    public MenuItemSummaryResponse updateItem(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            UpdateMenuItemRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);

        UUID targetSectionId = request.getSectionId();
        if (targetSectionId != null && !targetSectionId.equals(sectionId)) {
            item.setSection(requireScopedSection(menu, targetSectionId));
        }

        item.setSku(NormalizationUtils.normalizeUpper(request.getSku()));
        item.setName(NormalizationUtils.normalize(request.getName()));
        item.setDescription(NormalizationUtils.normalize(request.getDescription()));
        item.setBasePrice(request.getBasePrice());
        // Photos change through their own upload. An update only touches the photo when it names one ("" clears it);
        // a photo it drops is removed from storage once this is saved.
        if (request.getImageUrl() != null) {
            String nextImage = NormalizationUtils.normalize(request.getImageUrl());
            String previousImage = item.getImageUrl();
            if (!Objects.equals(nextImage, previousImage)) {
                item.setImageUrl(nextImage);
                if (previousImage != null) {
                    menuImageReleaser.releaseAfterCommit(previousImage);
                }
            }
        }
        item.setAvailable(Boolean.TRUE.equals(request.getAvailable()));
        if (request.getSendToKitchen() != null) {
            item.setSendToKitchen(request.getSendToKitchen());
        }
        applyOnlinePlacement(menu, item, request.getShowOnline(), request.getOnlineSectionId(), request.getOnlineSectionName());
        item.setDisplayOrder(request.getDisplayOrder());
        item.setIngredients(request.getIngredients());
        // Callers that know the extras fields always send the occasions (an empty list clears them); others leave
        // both as they are.
        if (request.getOccasionCodes() != null) {
            item.setOrderBeforeHours(request.getOrderBeforeHours());
            item.setOccasionCodes(occasionCodes(request.getOccasionCodes()));
        }

        return menuMapper.toMenuItemResponse(menuItemRepository.saveAndFlush(item));
    }

    @Transactional
    public MenuItemSummaryResponse updateItemAvailability(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            UpdateMenuItemAvailabilityRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        item.setAvailable(Boolean.TRUE.equals(request.getAvailable()));

        return menuMapper.toMenuItemResponse(menuItemRepository.saveAndFlush(item));
    }

    @Transactional
    public void deleteItem(Authentication authentication, UUID menuId, UUID sectionId, UUID itemId) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        if (menuVariantRepository.existsByMenuItemId(itemId) || menuItemOptionGroupRepository.existsByMenuItemId(itemId)) {
            throw new MenuItemDeletionBlockedException();
        }

        menuItemRepository.deleteKdsRoutingsOf(List.of(item.getId()));
        dishRecipeCleanup.releaseDishes(List.of(item.getId()));
        menuItemRepository.delete(item);
        if (item.getOnlineSection() != null) {
            onlineMenuService.removeIfEmpty(item.getOnlineSection(), item);
        }
        if (item.getImageUrl() != null) {
            menuImageReleaser.releaseAfterCommit(item.getImageUrl());
        }
    }

    // Uploads the dish's photo. A photo it replaces is removed from storage once this is saved; if saving fails, the
    // new photo is removed again.
    @Transactional
    public MenuItemSummaryResponse uploadItemImage(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            MultipartFile file
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuItem item = requireScopedItem(requireScopedSection(menu, sectionId), itemId);
        MenuImageUploads.CheckedPhoto photo = MenuImageUploads.check(file);

        String key = menuImageStorage.store(menu.getRestaurant().getId(), photo.content(), photo.contentType(), photo.extension());
        menuImageReleaser.discardOnRollback(key);
        String previousImage = item.getImageUrl();
        item.setImageUrl(menuImageStorage.urlFor(key));
        MenuItem saved = menuItemRepository.saveAndFlush(item);
        if (previousImage != null) {
            menuImageReleaser.releaseAfterCommit(previousImage);
        }
        return menuMapper.toMenuItemResponse(saved);
    }

    // Takes the photo off the dish (and out of storage, once saved, unless another dish still shows it).
    @Transactional
    public MenuItemSummaryResponse removeItemImage(Authentication authentication, UUID menuId, UUID sectionId, UUID itemId) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuItem item = requireScopedItem(requireScopedSection(menu, sectionId), itemId);
        String previousImage = item.getImageUrl();
        item.setImageUrl(null);
        MenuItem saved = menuItemRepository.saveAndFlush(item);
        if (previousImage != null) {
            menuImageReleaser.releaseAfterCommit(previousImage);
        }
        return menuMapper.toMenuItemResponse(saved);
    }

    private static String occasionCodes(List<String> codes) {
        if (codes == null) {
            return null;
        }
        String joined = String.join(",", codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase())
                .distinct()
                .toList());
        return joined.isEmpty() ? null : joined;
    }

    /** The dish, for someone who may see its menu. */
    public MenuItem requireReadableItem(Authentication authentication, UUID menuId, UUID sectionId, UUID itemId) {
        return requireScopedItem(requireScopedSection(requireAccessibleMenu(authentication, menuId), sectionId), itemId);
    }

    /** The dish, for someone who may change its menu (and not in an archived restaurant). */
    public MenuItem requireWritableItem(Authentication authentication, UUID menuId, UUID sectionId, UUID itemId) {
        return requireScopedItem(requireScopedSection(requireWritableMenu(authentication, menuId), sectionId), itemId);
    }

    /** The menu, for someone who may change it (and not in an archived restaurant). */
    public Menu requireWritableMenu(Authentication authentication, UUID menuId) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        return menu;
    }

    private Menu requireAccessibleMenu(Authentication authentication, UUID menuId) {
        ActorScope scope = actorScopeService.resolve(authentication);
        Menu menu = findExistingMenu(menuId);
        menuPolicy.assertCanAccess(scope, menu);
        return menu;
    }

    // "Show in online menu": off takes the dish offline; on (or naming a section) places it in that online section,
    // defaulting to one named like the dish's own section. Nothing given keeps the current placement.
    private void applyOnlinePlacement(Menu menu, MenuItem item, Boolean showOnline, UUID onlineSectionId, String onlineSectionName) {
        if (Boolean.FALSE.equals(showOnline)) {
            onlineMenuService.place(item, null);
            return;
        }
        boolean sectionGiven = onlineSectionId != null || (onlineSectionName != null && !onlineSectionName.isBlank());
        if (!Boolean.TRUE.equals(showOnline) && !sectionGiven) {
            return;
        }
        onlineMenuService.place(item, onlineMenuService.resolveSection(
                menu.getRestaurant(),
                onlineSectionId,
                sectionGiven ? onlineSectionName : item.getSection().getName()
        ));
    }

    private Menu requireManageableMenu(Authentication authentication, UUID menuId) {
        ActorScope scope = actorScopeService.resolve(authentication);
        Menu menu = findExistingMenu(menuId);
        menuPolicy.assertCanManage(scope, menu);
        return menu;
    }

    private Menu findExistingMenu(UUID menuId) {
        return menuRepository.findByIdAndRestaurantDeletedAtIsNull(menuId)
                .orElseThrow(MenuNotFoundException::new);
    }

    private MenuSection requireScopedSection(Menu menu, UUID sectionId) {
        MenuSection section = menuSectionRepository.findById(sectionId)
                .orElseThrow(MenuSectionNotFoundException::new);
        if (!section.getMenu().getId().equals(menu.getId())) {
            throw new MenuSectionMenuMismatchException();
        }
        return section;
    }

    private MenuItem requireScopedItem(MenuSection section, UUID itemId) {
        MenuItem item = menuItemRepository.findById(itemId)
                .orElseThrow(MenuItemNotFoundException::new);
        if (!item.getSection().getId().equals(section.getId())) {
            throw new MenuItemSectionMismatchException();
        }
        return item;
    }

    private void assertMenuWriteAllowed(Restaurant restaurant) {
        RestaurantStatus status = restaurant.getStatus();
        restaurantValidationService.validateManageableStatus(status);
        if (status == RestaurantStatus.ARCHIVED) {
            throw new AuthException("Archived restaurants cannot be modified", HttpStatus.BAD_REQUEST);
        }
    }
}
