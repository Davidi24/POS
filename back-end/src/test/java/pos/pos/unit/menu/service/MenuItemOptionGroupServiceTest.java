package pos.pos.unit.menu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuItemOptionGroupAlreadyExistsException;
import pos.pos.exception.menu.MenuItemOptionGroupItemMismatchException;
import pos.pos.exception.menu.MenuItemOptionGroupRestaurantMismatchException;
import pos.pos.menu.dto.CreateMenuItemOptionGroupRequest;
import pos.pos.menu.dto.MenuItemOptionGroupSummaryResponse;
import pos.pos.menu.dto.UpdateMenuItemOptionGroupRequest;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.entity.OptionGroupType;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.OptionGroupRepository;
import pos.pos.menu.service.MenuItemOptionGroupService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuItemOptionGroupService")
class MenuItemOptionGroupServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000511");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000512");
    private static final UUID FOREIGN_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000513");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000514");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000515");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000516");
    private static final UUID GROUP_ID = UUID.fromString("00000000-0000-0000-0000-000000000517");
    private static final UUID LINK_ID = UUID.fromString("00000000-0000-0000-0000-000000000518");
    private static final UUID FOREIGN_LINK_ID = UUID.fromString("00000000-0000-0000-0000-000000000519");

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private MenuSectionRepository menuSectionRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private MenuItemOptionGroupRepository menuItemOptionGroupRepository;

    @Mock
    private OptionGroupRepository optionGroupRepository;

    private final MenuMapper menuMapper = new MenuMapper();
    private final MenuPolicy menuPolicy = new MenuPolicy(new RestaurantPolicy());
    private MenuItemOptionGroupService menuItemOptionGroupService;

    private StubActorScopeService actorScopeService;
    private StubRestaurantValidationService restaurantValidationService;

    @BeforeEach
    void setUp() {
        actorScopeService = new StubActorScopeService();
        restaurantValidationService = new StubRestaurantValidationService();
        menuItemOptionGroupService = new MenuItemOptionGroupService(
                menuRepository,
                menuSectionRepository,
                menuItemRepository,
                menuItemOptionGroupRepository,
                optionGroupRepository,
                menuMapper,
                actorScopeService,
                menuPolicy,
                restaurantValidationService
        );
    }

    @Test
    @DisplayName("getOptionGroups should return links for the scoped item")
    void shouldReturnLinks() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE, RESTAURANT_ID);
        MenuSection section = section(menu);
        MenuItem item = item(section);
        MenuItemOptionGroup link = link(item, optionGroup(restaurant(RESTAURANT_ID), GROUP_ID));

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(menuItemOptionGroupRepository.findByMenuItemIdOrdered(ITEM_ID)).willReturn(List.of(link));

        List<MenuItemOptionGroupSummaryResponse> response = menuItemOptionGroupService.getOptionGroups(
                authentication,
                MENU_ID,
                SECTION_ID,
                ITEM_ID
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getLinkId()).isEqualTo(LINK_ID);
        assertThat(response.get(0).getOptionGroupId()).isEqualTo(GROUP_ID);
    }

    @Test
    @DisplayName("createOptionGroupLink should reject links to option groups from another restaurant")
    void shouldRejectForeignRestaurantOptionGroup() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE, RESTAURANT_ID);
        MenuSection section = section(menu);
        MenuItem item = item(section);
        OptionGroup foreignGroup = optionGroup(restaurant(FOREIGN_RESTAURANT_ID), GROUP_ID);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(optionGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(foreignGroup));

        assertThatThrownBy(() -> menuItemOptionGroupService.createOptionGroupLink(
                authentication,
                MENU_ID,
                SECTION_ID,
                ITEM_ID,
                CreateMenuItemOptionGroupRequest.builder()
                        .optionGroupId(GROUP_ID)
                        .build()
        )).isInstanceOf(MenuItemOptionGroupRestaurantMismatchException.class)
                .hasMessage("Option group does not belong to the same restaurant as this menu item");
    }

    @Test
    @DisplayName("createOptionGroupLink should reject duplicate menu item and option group links")
    void shouldRejectDuplicateLink() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE, RESTAURANT_ID);
        MenuSection section = section(menu);
        MenuItem item = item(section);
        OptionGroup group = optionGroup(restaurant(RESTAURANT_ID), GROUP_ID);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(optionGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(menuItemOptionGroupRepository.existsByMenuItemIdAndOptionGroupId(ITEM_ID, GROUP_ID)).willReturn(true);

        assertThatThrownBy(() -> menuItemOptionGroupService.createOptionGroupLink(
                authentication,
                MENU_ID,
                SECTION_ID,
                ITEM_ID,
                CreateMenuItemOptionGroupRequest.builder()
                        .optionGroupId(GROUP_ID)
                        .build()
        )).isInstanceOf(MenuItemOptionGroupAlreadyExistsException.class)
                .hasMessage("Option group already linked to this menu item");
    }

    @Test
    @DisplayName("updateOptionGroupLink should reject invalid override bounds")
    void shouldRejectInvalidSelectionBounds() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE, RESTAURANT_ID);
        MenuSection section = section(menu);
        MenuItem item = item(section);
        MenuItemOptionGroup link = link(item, optionGroup(restaurant(RESTAURANT_ID), GROUP_ID));

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(menuItemOptionGroupRepository.findById(LINK_ID)).willReturn(Optional.of(link));

        assertThatThrownBy(() -> menuItemOptionGroupService.updateOptionGroupLink(
                authentication,
                MENU_ID,
                SECTION_ID,
                ITEM_ID,
                LINK_ID,
                UpdateMenuItemOptionGroupRequest.builder()
                        .displayOrder(1)
                        .minSelectOverride(3)
                        .maxSelectOverride(2)
                        .build()
        )).isInstanceOf(AuthException.class)
                .hasMessage("minSelectOverride must be less than or equal to maxSelectOverride");
    }

    @Test
    @DisplayName("deleteOptionGroupLink should reject links that belong to another item")
    void shouldRejectMismatchedLink() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE, RESTAURANT_ID);
        MenuSection section = section(menu);
        MenuItem item = item(section);
        MenuItem foreignItem = item(section);
        foreignItem.setId(UUID.fromString("00000000-0000-0000-0000-000000000520"));
        MenuItemOptionGroup foreignLink = link(foreignItem, optionGroup(restaurant(RESTAURANT_ID), GROUP_ID));
        foreignLink.setId(FOREIGN_LINK_ID);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(menuItemOptionGroupRepository.findById(FOREIGN_LINK_ID)).willReturn(Optional.of(foreignLink));

        assertThatThrownBy(() -> menuItemOptionGroupService.deleteOptionGroupLink(
                authentication,
                MENU_ID,
                SECTION_ID,
                ITEM_ID,
                FOREIGN_LINK_ID
        )).isInstanceOf(MenuItemOptionGroupItemMismatchException.class)
                .hasMessage("Menu item option group link does not belong to this item");
    }

    @Test
    @DisplayName("createOptionGroupLink should persist defaults and overrides")
    void shouldCreateLink() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE, RESTAURANT_ID);
        MenuSection section = section(menu);
        MenuItem item = item(section);
        OptionGroup group = optionGroup(restaurant(RESTAURANT_ID), GROUP_ID);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(optionGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group));
        given(menuItemOptionGroupRepository.existsByMenuItemIdAndOptionGroupId(ITEM_ID, GROUP_ID)).willReturn(false);
        given(menuItemOptionGroupRepository.saveAndFlush(any(MenuItemOptionGroup.class))).willAnswer(invocation -> {
            MenuItemOptionGroup saved = invocation.getArgument(0);
            saved.setId(LINK_ID);
            return saved;
        });

        MenuItemOptionGroupSummaryResponse response = menuItemOptionGroupService.createOptionGroupLink(
                authentication,
                MENU_ID,
                SECTION_ID,
                ITEM_ID,
                CreateMenuItemOptionGroupRequest.builder()
                        .optionGroupId(GROUP_ID)
                        .minSelectOverride(1)
                        .maxSelectOverride(2)
                        .requiredOverride(true)
                        .build()
        );

        assertThat(response.getLinkId()).isEqualTo(LINK_ID);
        verify(menuItemOptionGroupRepository).saveAndFlush(any(MenuItemOptionGroup.class));
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("owner@pos.local")
                        .username("owner")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    private ActorScope actorScope(boolean superAdmin, UUID restaurantId) {
        return new ActorScope(
                User.builder()
                        .id(ACTOR_ID)
                        .email("owner@pos.local")
                        .username("owner")
                        .passwordHash("stored")
                        .firstName("Owner")
                        .lastName("Main")
                        .status("ACTIVE")
                        .isActive(true)
                        .restaurantId(restaurantId)
                        .build(),
                superAdmin,
                Set.of(superAdmin ? "SUPER_ADMIN" : "OWNER"),
                Set.of("MENUS_READ", "MENUS_CREATE", "MENUS_UPDATE", "MENUS_DELETE")
        );
    }

    private Restaurant restaurant(UUID restaurantId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("POS Main");
        restaurant.setCode("POS_MAIN");
        restaurant.setActive(true);
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setOwnerId(ACTOR_ID);
        return restaurant;
    }

    private Menu menu(RestaurantStatus status, UUID restaurantId) {
        Restaurant restaurant = restaurant(restaurantId);
        restaurant.setStatus(status);
        restaurant.setActive(status == RestaurantStatus.ACTIVE);

        Menu menu = new Menu();
        menu.setId(MENU_ID);
        menu.setRestaurant(restaurant);
        menu.setCode("BREAKFAST");
        menu.setName("Breakfast");
        menu.setActive(true);
        menu.setDisplayOrder(1);
        return menu;
    }

    private MenuSection section(Menu menu) {
        MenuSection section = new MenuSection();
        section.setId(SECTION_ID);
        section.setMenu(menu);
        section.setName("Mains");
        section.setDescription("Main dishes");
        section.setActive(true);
        section.setDisplayOrder(1);
        return section;
    }

    private MenuItem item(MenuSection section) {
        MenuItem item = new MenuItem();
        item.setId(ITEM_ID);
        item.setSection(section);
        item.setSku("BRG-001");
        item.setName("Burger");
        item.setAvailable(true);
        item.setDisplayOrder(1);
        return item;
    }

    private OptionGroup optionGroup(Restaurant restaurant, UUID groupId) {
        OptionGroupType type = new OptionGroupType();
        type.setId(UUID.fromString("00000000-0000-0000-0000-000000000521"));
        type.setCode("SINGLE_SELECT");
        type.setName("Single Select");

        OptionGroup group = new OptionGroup();
        group.setId(groupId);
        group.setRestaurant(restaurant);
        group.setType(type);
        group.setName("Sauces");
        group.setDescription("Choose a sauce");
        group.setMinSelect(0);
        group.setMaxSelect(2);
        group.setRequired(false);
        group.setActive(true);
        group.setDisplayOrder(1);
        return group;
    }

    private MenuItemOptionGroup link(MenuItem item, OptionGroup optionGroup) {
        MenuItemOptionGroup link = new MenuItemOptionGroup();
        link.setId(LINK_ID);
        link.setMenuItem(item);
        link.setOptionGroup(optionGroup);
        link.setDisplayOrder(1);
        return link;
    }

    private static class StubActorScopeService extends ActorScopeService {

        private ActorScope scope;

        StubActorScopeService() {
            super(null, null);
        }

        @Override
        public ActorScope resolve(Authentication authentication) {
            return scope;
        }
    }

    private static class StubRestaurantValidationService extends RestaurantValidationService {

        StubRestaurantValidationService() {
            super(null, null);
        }

        @Override
        public void validateManageableStatus(RestaurantStatus status) {
        }
    }
}
