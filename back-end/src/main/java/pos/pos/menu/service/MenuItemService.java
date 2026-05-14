package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuItemDeletionBlockedException;
import pos.pos.exception.menu.MenuItemNotFoundException;
import pos.pos.exception.menu.MenuItemSectionMismatchException;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.exception.menu.MenuSectionMenuMismatchException;
import pos.pos.exception.menu.MenuSectionNotFoundException;
import pos.pos.menu.dto.CreateMenuItemRequest;
import pos.pos.menu.dto.MenuItemSummaryResponse;
import pos.pos.menu.dto.UpdateMenuItemAvailabilityRequest;
import pos.pos.menu.dto.UpdateMenuItemRequest;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
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
import java.util.UUID;

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

        return items.stream()
                .map(item -> toMenuItemResponse(item, includeVariants, includeOptionGroups))
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
        return toMenuItemResponse(item, includeVariants, includeOptionGroups);
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
        item.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());

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

        item.setSku(NormalizationUtils.normalizeUpper(request.getSku()));
        item.setName(NormalizationUtils.normalize(request.getName()));
        item.setDescription(NormalizationUtils.normalize(request.getDescription()));
        item.setBasePrice(request.getBasePrice());
        item.setImageUrl(NormalizationUtils.normalize(request.getImageUrl()));
        item.setAvailable(Boolean.TRUE.equals(request.getAvailable()));
        item.setDisplayOrder(request.getDisplayOrder());

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

        menuItemRepository.delete(item);
    }

    private MenuItemSummaryResponse toMenuItemResponse(MenuItem item, boolean includeVariants, boolean includeOptionGroups) {
        List<MenuVariant> variants = includeVariants
                ? menuVariantRepository.findByMenuItemIdOrderByDisplayOrderAscNameAsc(item.getId())
                : null;
        List<MenuItemOptionGroup> optionGroups = includeOptionGroups
                ? menuItemOptionGroupRepository.findByMenuItemIdOrdered(item.getId())
                : null;
        return menuMapper.toMenuItemResponse(item, variants, optionGroups);
    }

    private Menu requireAccessibleMenu(Authentication authentication, UUID menuId) {
        ActorScope scope = actorScopeService.resolve(authentication);
        Menu menu = findExistingMenu(menuId);
        menuPolicy.assertCanAccess(scope, menu);
        return menu;
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
