package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuItemNotFoundException;
import pos.pos.exception.menu.MenuItemSectionMismatchException;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.exception.menu.MenuSectionMenuMismatchException;
import pos.pos.exception.menu.MenuSectionNotFoundException;
import pos.pos.exception.menu.MenuVariantItemMismatchException;
import pos.pos.exception.menu.MenuVariantNameAlreadyExistsException;
import pos.pos.exception.menu.MenuVariantNotFoundException;
import pos.pos.menu.dto.CreateMenuVariantRequest;
import pos.pos.menu.dto.MenuVariantSummaryResponse;
import pos.pos.menu.dto.UpdateMenuVariantRequest;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
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
public class MenuVariantService {

    private final MenuRepository menuRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuVariantRepository menuVariantRepository;
    private final MenuMapper menuMapper;
    private final ActorScopeService actorScopeService;
    private final MenuPolicy menuPolicy;
    private final RestaurantValidationService restaurantValidationService;

    @Transactional(readOnly = true)
    public List<MenuVariantSummaryResponse> getVariants(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId
    ) {
        Menu menu = requireAccessibleMenu(authentication, menuId);
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        return menuVariantRepository.findByMenuItemIdOrderByDisplayOrderAscNameAsc(item.getId()).stream()
                .map(menuMapper::toMenuVariantSummaryResponse)
                .toList();
    }

    @Transactional
    public MenuVariantSummaryResponse createVariant(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            CreateMenuVariantRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);

        String normalizedName = NormalizationUtils.normalize(request.getName());
        assertUniqueName(itemId, normalizedName, null);

        MenuVariant variant = new MenuVariant();
        variant.setMenuItem(item);
        variant.setName(normalizedName);
        variant.setSku(NormalizationUtils.normalizeUpper(request.getSku()));
        variant.setPriceDelta(request.getPriceDelta());
        variant.setDefault(Boolean.TRUE.equals(request.getIsDefault()));
        variant.setActive(request.getActive() == null || request.getActive());
        variant.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());

        if (variant.isDefault()) {
            clearOtherDefaults(itemId, null);
        }

        return menuMapper.toMenuVariantSummaryResponse(menuVariantRepository.saveAndFlush(variant));
    }

    @Transactional
    public MenuVariantSummaryResponse updateVariant(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            UUID variantId,
            UpdateMenuVariantRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        MenuVariant variant = requireScopedVariant(item, variantId);

        String normalizedName = NormalizationUtils.normalize(request.getName());
        assertUniqueName(itemId, normalizedName, variantId);

        if (Boolean.TRUE.equals(request.getIsDefault())) {
            clearOtherDefaults(itemId, variantId);
        }

        variant.setName(normalizedName);
        variant.setSku(NormalizationUtils.normalizeUpper(request.getSku()));
        variant.setPriceDelta(request.getPriceDelta());
        variant.setDefault(Boolean.TRUE.equals(request.getIsDefault()));
        variant.setActive(Boolean.TRUE.equals(request.getActive()));
        variant.setDisplayOrder(request.getDisplayOrder());

        return menuMapper.toMenuVariantSummaryResponse(menuVariantRepository.saveAndFlush(variant));
    }

    @Transactional
    public void deleteVariant(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            UUID variantId
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        MenuVariant variant = requireScopedVariant(item, variantId);
        menuVariantRepository.delete(variant);
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

    private MenuVariant requireScopedVariant(MenuItem item, UUID variantId) {
        MenuVariant variant = menuVariantRepository.findById(variantId)
                .orElseThrow(MenuVariantNotFoundException::new);
        if (!variant.getMenuItem().getId().equals(item.getId())) {
            throw new MenuVariantItemMismatchException();
        }
        return variant;
    }

    private void assertUniqueName(UUID itemId, String name, UUID variantIdToExclude) {
        boolean exists = variantIdToExclude == null
                ? menuVariantRepository.existsByMenuItemIdAndName(itemId, name)
                : menuVariantRepository.existsByMenuItemIdAndNameAndIdNot(itemId, name, variantIdToExclude);
        if (exists) {
            throw new MenuVariantNameAlreadyExistsException();
        }
    }

    private void clearOtherDefaults(UUID itemId, UUID variantIdToExclude) {
        menuVariantRepository.findByMenuItemIdOrderByDisplayOrderAscNameAsc(itemId).stream()
                .filter(MenuVariant::isDefault)
                .filter(variant -> variantIdToExclude == null || !variant.getId().equals(variantIdToExclude))
                .forEach(variant -> variant.setDefault(false));
    }

    private void assertMenuWriteAllowed(Restaurant restaurant) {
        RestaurantStatus status = restaurant.getStatus();
        restaurantValidationService.validateManageableStatus(status);
        if (status == RestaurantStatus.ARCHIVED) {
            throw new AuthException("Archived restaurants cannot be modified", HttpStatus.BAD_REQUEST);
        }
    }
}
