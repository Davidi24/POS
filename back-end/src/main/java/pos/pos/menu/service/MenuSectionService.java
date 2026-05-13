package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.exception.menu.MenuSectionDeletionBlockedException;
import pos.pos.exception.menu.MenuSectionMenuMismatchException;
import pos.pos.exception.menu.MenuSectionNameAlreadyExistsException;
import pos.pos.exception.menu.MenuSectionNotFoundException;
import pos.pos.menu.dto.CreateMenuSectionRequest;
import pos.pos.menu.dto.MenuSectionSummaryResponse;
import pos.pos.menu.dto.UpdateMenuSectionRequest;
import pos.pos.menu.dto.UpdateMenuSectionStatusRequest;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuSectionService {

    private final MenuRepository menuRepository;
    private final MenuSectionRepository menuSectionRepository;
    private final MenuItemRepository menuItemRepository;
    private final MenuMapper menuMapper;
    private final ActorScopeService actorScopeService;
    private final MenuPolicy menuPolicy;
    private final RestaurantValidationService restaurantValidationService;

    @Transactional(readOnly = true)
    public List<MenuSectionSummaryResponse> getSections(
            Authentication authentication,
            UUID menuId,
            Boolean active,
            boolean includeItems
    ) {
        Menu menu = requireAccessibleMenu(authentication, menuId);
        List<MenuSection> sections = active == null
                ? menuSectionRepository.findByMenuIdOrderByDisplayOrderAscNameAsc(menu.getId())
                : menuSectionRepository.findByMenuIdAndActiveOrderByDisplayOrderAscNameAsc(menu.getId(), active);
        Map<UUID, List<MenuItem>> itemsBySectionId = includeItems
                ? menuItemRepository.findByMenuIdOrdered(menu.getId()).stream()
                .collect(Collectors.groupingBy(
                        item -> item.getSection().getId(),
                        Collectors.mapping(Function.identity(), Collectors.toList())
                ))
                : Map.of();

        return sections.stream()
                .map(section -> menuMapper.toMenuSectionResponse(section, itemsBySectionId.get(section.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public MenuSectionSummaryResponse getSection(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            boolean includeItems
    ) {
        Menu menu = requireAccessibleMenu(authentication, menuId);
        MenuSection section = requireScopedSection(menu, sectionId);
        List<MenuItem> items = includeItems
                ? menuItemRepository.findBySectionIdOrderByDisplayOrderAscNameAsc(sectionId)
                : null;

        return menuMapper.toMenuSectionResponse(section, items);
    }

    @Transactional
    public MenuSectionSummaryResponse createSection(
            Authentication authentication,
            UUID menuId,
            CreateMenuSectionRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());

        String normalizedName = NormalizationUtils.normalize(request.getName());
        assertUniqueName(menuId, normalizedName, null);

        MenuSection section = new MenuSection();
        section.setMenu(menu);
        section.setName(normalizedName);
        section.setDescription(NormalizationUtils.normalize(request.getDescription()));
        section.setActive(request.getActive() == null || request.getActive());
        section.setDisplayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder());

        return menuMapper.toMenuSectionResponse(menuSectionRepository.saveAndFlush(section));
    }

    @Transactional
    public MenuSectionSummaryResponse updateSection(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UpdateMenuSectionRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);

        String normalizedName = NormalizationUtils.normalize(request.getName());
        assertUniqueName(menuId, normalizedName, sectionId);

        section.setName(normalizedName);
        section.setDescription(NormalizationUtils.normalize(request.getDescription()));
        section.setActive(Boolean.TRUE.equals(request.getActive()));
        section.setDisplayOrder(request.getDisplayOrder());

        return menuMapper.toMenuSectionResponse(menuSectionRepository.saveAndFlush(section));
    }

    @Transactional
    public MenuSectionSummaryResponse updateSectionStatus(
            Authentication authentication,
            UUID menuId,
            UUID sectionId,
            UpdateMenuSectionStatusRequest request
    ) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        section.setActive(Boolean.TRUE.equals(request.getActive()));

        return menuMapper.toMenuSectionResponse(menuSectionRepository.saveAndFlush(section));
    }

    @Transactional
    public void deleteSection(Authentication authentication, UUID menuId, UUID sectionId) {
        Menu menu = requireManageableMenu(authentication, menuId);
        assertMenuWriteAllowed(menu.getRestaurant());
        MenuSection section = requireScopedSection(menu, sectionId);
        if (menuItemRepository.existsBySectionId(sectionId)) {
            throw new MenuSectionDeletionBlockedException();
        }

        menuSectionRepository.delete(section);
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

    private void assertUniqueName(UUID menuId, String name, UUID sectionIdToExclude) {
        boolean exists = sectionIdToExclude == null
                ? menuSectionRepository.existsByMenuIdAndName(menuId, name)
                : menuSectionRepository.existsByMenuIdAndNameAndIdNot(menuId, name, sectionIdToExclude);
        if (exists) {
            throw new MenuSectionNameAlreadyExistsException();
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
