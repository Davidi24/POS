package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuItemNotFoundException;
import pos.pos.exception.menu.MenuItemOptionGroupAlreadyExistsException;
import pos.pos.exception.menu.MenuItemOptionGroupItemMismatchException;
import pos.pos.exception.menu.MenuItemOptionGroupNotFoundException;
import pos.pos.exception.menu.MenuItemOptionGroupRestaurantMismatchException;
import pos.pos.exception.menu.MenuItemSectionMismatchException;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.exception.menu.MenuSectionMenuMismatchException;
import pos.pos.exception.menu.MenuSectionNotFoundException;
import pos.pos.exception.menu.OptionGroupNotFoundException;
import pos.pos.menu.dto.CreateMenuItemOptionGroupRequest;
import pos.pos.menu.dto.MenuItemOptionGroupSummaryResponse;
import pos.pos.menu.dto.UpdateMenuItemOptionGroupRequest;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MenuItemOptionGroupService {

    private final MenuRepository menuRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuItemOptionGroupRepository menuItemOptionGroupRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final MenuMapper menuMapper;
    private final ActorScopeService actorScopeService;
    private final MenuPolicy menuPolicy;
    private final RestaurantValidationService restaurantValidationService;

    @Transactional(readOnly = true)
    public List<MenuItemOptionGroupSummaryResponse> getOptionGroups(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId
    ) {
        Menu menu = requireAccessibleMenu(authentication, menuId);
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        return menuItemOptionGroupRepository.findByMenuItemIdOrdered(item.getId()).stream()
                .map(menuMapper::toMenuItemOptionGroupSummaryResponse)
                .toList();
    }

    @Transactional
    public MenuItemOptionGroupSummaryResponse createOptionGroupLink(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            CreateMenuItemOptionGroupRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        OptionGroup optionGroup = requireScopedOptionGroup(menu.getRestaurant().getId(), request.getOptionGroupId());
        validateSelectionBounds(request.getMinSelectOverride(), request.getMaxSelectOverride());

        if (menuItemOptionGroupRepository.existsByMenuItemIdAndOptionGroupId(item.getId(), optionGroup.getId())) {
            throw new MenuItemOptionGroupAlreadyExistsException();
        }

        MenuItemOptionGroup link = new MenuItemOptionGroup();
        link.setMenuItem(item);
        link.setOptionGroup(optionGroup);
        link.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        link.setMinSelectOverride(request.getMinSelectOverride());
        link.setMaxSelectOverride(request.getMaxSelectOverride());
        link.setRequiredOverride(request.getRequiredOverride());

        return menuMapper.toMenuItemOptionGroupSummaryResponse(menuItemOptionGroupRepository.saveAndFlush(link));
    }

    @Transactional
    public MenuItemOptionGroupSummaryResponse updateOptionGroupLink(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            UUID linkId,
            UpdateMenuItemOptionGroupRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        MenuItemOptionGroup link = requireScopedLink(item, linkId);
        validateSelectionBounds(request.getMinSelectOverride(), request.getMaxSelectOverride());

        link.setDisplayOrder(request.getDisplayOrder());
        link.setMinSelectOverride(request.getMinSelectOverride());
        link.setMaxSelectOverride(request.getMaxSelectOverride());
        link.setRequiredOverride(request.getRequiredOverride());

        return menuMapper.toMenuItemOptionGroupSummaryResponse(menuItemOptionGroupRepository.saveAndFlush(link));
    }

    @Transactional
    public void deleteOptionGroupLink(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UUID itemId,
            UUID linkId
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        MenuItem item = requireScopedItem(section, itemId);
        MenuItemOptionGroup link = requireScopedLink(item, linkId);
        menuItemOptionGroupRepository.delete(link);
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

    private OptionGroup requireScopedOptionGroup(UUID restaurantId, UUID optionGroupId) {
        OptionGroup optionGroup = optionGroupRepository.findById(optionGroupId)
                .orElseThrow(OptionGroupNotFoundException::new);
        if (!optionGroup.getRestaurant().getId().equals(restaurantId)) {
            throw new MenuItemOptionGroupRestaurantMismatchException();
        }
        return optionGroup;
    }

    private MenuItemOptionGroup requireScopedLink(MenuItem item, UUID linkId) {
        MenuItemOptionGroup link = menuItemOptionGroupRepository.findById(linkId)
                .orElseThrow(MenuItemOptionGroupNotFoundException::new);
        if (!link.getMenuItem().getId().equals(item.getId())) {
            throw new MenuItemOptionGroupItemMismatchException();
        }
        return link;
    }

    private void validateSelectionBounds(Integer minSelectOverride, Integer maxSelectOverride) {
        if (minSelectOverride != null && maxSelectOverride != null && minSelectOverride > maxSelectOverride) {
            throw new AuthException(
                    "minSelectOverride must be less than or equal to maxSelectOverride",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void assertMenuWriteAllowed(Restaurant restaurant) {
        RestaurantStatus status = restaurant.getStatus();
        restaurantValidationService.validateManageableStatus(status);
        if (status == RestaurantStatus.ARCHIVED) {
            throw new AuthException("Archived restaurants cannot be modified", HttpStatus.BAD_REQUEST);
        }
    }
}
