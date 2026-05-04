package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuCodeAlreadyExistsException;
import pos.pos.exception.menu.MenuDeletionBlockedException;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.menu.dto.CreateMenuRequest;
import pos.pos.menu.dto.MenuResponse;
import pos.pos.menu.dto.UpdateMenuRequest;
import pos.pos.menu.dto.UpdateMenuStatusRequest;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.util.MenuCodeNormalizer;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final MenuRepository menuRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuMapper menuMapper;
    private final ActorScopeService actorScopeService;
    private final MenuPolicy menuPolicy;
    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantValidationService restaurantValidationService;

    @Transactional(readOnly = true)
    public PageResponse<MenuResponse> getMenus(
            Authentication authentication,
            UUID restaurantId,
            Boolean active,
            String search,
            Integer page,
            Integer size,
            String sortBy,
            String direction
    ) {
        ActorScope scope = actorScopeService.resolve(authentication);
        if (restaurantId != null) {
            restaurantScopeService.requireAccessibleRestaurant(scope, restaurantId);
        }

        Pageable pageable = PageRequest.of(
                page == null ? 0 : page,
                size == null ? DEFAULT_PAGE_SIZE : size,
                resolveSort(sortBy, direction)
        );

        String searchLike = NormalizationUtils.normalizeLowerLike(search);

        Page<Menu> menusPage = menuRepository.searchVisibleMenus(
                restaurantId,
                active,
                searchLike,
                scope.superAdmin(),
                scope.restaurantId(),
                scope.userId(),
                pageable
        );
        List<MenuResponse> items = menusPage.getContent().stream()
                .map(menuMapper::toMenuResponse)
                .toList();

        return PageResponse.from(new PageImpl<>(items, pageable, menusPage.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public MenuResponse getMenu(Authentication authentication, UUID menuId, boolean includeSections, boolean includeItems) {
        ActorScope scope = actorScopeService.resolve(authentication);
        Menu menu = findExistingMenu(menuId);
        menuPolicy.assertCanAccess(scope, menu);
        if (!includeSections && !includeItems) {
            return menuMapper.toMenuResponse(menu);
        }

        List<MenuSection> sections = menuSectionRepository.findByMenuIdOrderByDisplayOrderAscNameAsc(menuId);
        Map<UUID, List<MenuItem>> itemsBySectionId = includeItems
                ? menuItemRepository.findByMenuIdOrdered(menuId).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getSection().getId(),
                        Collectors.mapping(Function.identity(), Collectors.toList())
                ))
                : Map.of();

        return menuMapper.toMenuResponse(menu, sections, itemsBySectionId);
    }

    //checked
    @Transactional
    public MenuResponse createMenu(Authentication authentication, CreateMenuRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, request.getRestaurantId());
        assertMenuWriteAllowed(restaurant);

        String normalizedCode = resolveCreateCode(request.getCode(), request.getName());
        assertUniqueCode(restaurant.getId(), normalizedCode, null);

        User actor = restaurantScopeService.currentActor(authentication);
        Menu menu = new Menu();
        menu.setRestaurant(restaurant);
        menu.setCode(normalizedCode);
        menu.setName(NormalizationUtils.normalize(request.getName()));
        menu.setDescription(NormalizationUtils.normalize(request.getDescription()));
        menu.setActive(request.getActive() == null || request.getActive());
        menu.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());
        menu.setCreatedBy(actor);
        menu.setUpdatedBy(actor);

        return menuMapper.toMenuResponse(menuRepository.saveAndFlush(menu));
    }

    //checked
    @Transactional
    public MenuResponse updateMenu(Authentication authentication, UUID menuId, UpdateMenuRequest request) {
        Menu menu = requireManageableMenu(authentication, menuId); //this line protects the whole update

        String normalizedCode = resolveUpdateCode(request.getCode(), menu.getCode());//decides what the menu code should be
        assertUniqueCode(menu.getRestaurant().getId(), normalizedCode, menu.getId());//menu code must be unique inside one restaurant

        menu.setCode(normalizedCode); //saves the final menu code
        menu.setName(NormalizationUtils.normalize(request.getName()));//normalize space "  Breakfast Menu  " → "Breakfast Menu"
        menu.setDescription(NormalizationUtils.normalize(request.getDescription()));
        menu.setActive(Boolean.TRUE.equals(request.getActive()));//is the menu active or not
        menu.setDisplayOrder(request.getDisplayOrder());//saves the order position of the menu
        menu.setUpdatedBy(restaurantScopeService.currentActor(authentication));//stores who updated the menu

        return menuMapper.toMenuResponse(menuRepository.saveAndFlush(menu));
    }

    @Transactional
    public MenuResponse updateMenuStatus(Authentication authentication, UUID menuId, UpdateMenuStatusRequest request) {
        Menu menu = requireManageableMenu(authentication, menuId);
        menu.setActive(Boolean.TRUE.equals(request.getActive()));
        menu.setUpdatedBy(restaurantScopeService.currentActor(authentication));

        return menuMapper.toMenuResponse(menuRepository.saveAndFlush(menu));
    }

    @Transactional
    public void deleteMenu(Authentication authentication, UUID menuId) {
        Menu menu = requireManageableMenu(authentication, menuId);
        if (menuSectionRepository.existsByMenuId(menuId)) {
            throw new MenuDeletionBlockedException();
        }

        menuRepository.delete(menu);
    }

    private Menu findExistingMenu(UUID menuId) {
        return menuRepository.findByIdAndRestaurantDeletedAtIsNull(menuId)
                .orElseThrow(MenuNotFoundException::new);
    }

    //before updating, get the menu safely while going through some checks such as:
    private Menu requireManageableMenu(Authentication authentication, UUID menuId) {
        ActorScope scope = actorScopeService.resolve(authentication);
        Menu menu = findExistingMenu(menuId);// if menu doesnt exist stop,
        menuPolicy.assertCanManage(scope, menu); //if user not allowed stop,
        assertMenuWriteAllowed(menu.getRestaurant()); //if restaurant cannot be edited stop
        return menu; //otherwise return the menu
    }

    //checks if the restaurant can be modified
    private void assertMenuWriteAllowed(Restaurant restaurant) {
        RestaurantStatus status = restaurant.getStatus();
        restaurantValidationService.validateManageableStatus(status);
        if (status == RestaurantStatus.ARCHIVED) {
            throw new AuthException("Archived restaurants cannot be modified", HttpStatus.BAD_REQUEST);
        }
    }

    //checks that no other menu in the same restaurant already uses that code
    private void assertUniqueCode(UUID restaurantId, String code, UUID menuIdToExclude) {
        boolean exists = menuIdToExclude == null
                ? menuRepository.existsByRestaurantIdAndCode(restaurantId, code)
                : menuRepository.existsByRestaurantIdAndCodeAndIdNot(restaurantId, code, menuIdToExclude);
        if (exists) {
            throw new MenuCodeAlreadyExistsException();
        }
    }

    private String resolveCreateCode(String requestedCode, String fallbackName) {
        String normalizedCode = MenuCodeNormalizer.normalize(
                NormalizationUtils.normalize(requestedCode) == null ? fallbackName : requestedCode
        );
        if (normalizedCode == null) {
            throw new AuthException("Name is required", HttpStatus.BAD_REQUEST);
        }
        return normalizedCode;
    }

    //If the user sent a new Menu code, use it.
    //If the user did not send a code, keep the old database code.
    private String resolveUpdateCode(String requestedCode, String existingCode) {
        String rawCode = NormalizationUtils.normalize(requestedCode) == null ? existingCode : requestedCode;
        String normalizedCode = MenuCodeNormalizer.normalize(rawCode);
        if (normalizedCode == null) {
            throw new AuthException("Code is required", HttpStatus.BAD_REQUEST);
        }
        return normalizedCode;
    }

    private Sort resolveSort(String sortBy, String direction) {
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(
                    NormalizationUtils.normalize(direction) == null ? "asc" : direction
            );
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid sort direction", HttpStatus.BAD_REQUEST);
        }

        String normalizedSortBy = NormalizationUtils.normalizeLower(sortBy);
        String property = switch (normalizedSortBy == null ? "displayorder" : normalizedSortBy) {
            case "displayorder", "display_order" -> "displayOrder";
            case "createdat", "created_at" -> "createdAt";
            case "updatedat", "updated_at" -> "updatedAt";
            case "name" -> "name";
            case "code" -> "code";
            default -> throw new AuthException("Invalid sortBy value", HttpStatus.BAD_REQUEST);
        };

        return Sort.by(sortDirection, property).and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
