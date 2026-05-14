package pos.pos.unit.menu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.menu.MenuDeletionBlockedException;
import pos.pos.menu.dto.CreateMenuRequest;
import pos.pos.menu.dto.MenuResponse;
import pos.pos.menu.dto.UpdateMenuStatusRequest;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.menu.service.MenuService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuService")
class MenuServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private MenuSectionRepository menuSectionRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private MenuVariantRepository menuVariantRepository;

    @Mock
    private MenuItemOptionGroupRepository menuItemOptionGroupRepository;

    @Mock
    private ActorScopeService actorScopeService;

    @Spy
    private MenuPolicy menuPolicy = new MenuPolicy(new RestaurantPolicy());

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private RestaurantValidationService restaurantValidationService;

    @Spy
    private MenuMapper menuMapper = new MenuMapper();

    @InjectMocks
    private MenuService menuService;

    @Test
    @DisplayName("getMenus should apply actor scope, filters, and requested sorting")
    void shouldReturnPagedMenus() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);
        Menu menu = menu(restaurant);

        given(actorScopeService.resolve(authentication)).willReturn(scope);
        given(restaurantScopeService.requireAccessibleRestaurant(scope, RESTAURANT_ID)).willReturn(restaurant);
        given(menuRepository.searchVisibleMenus(
                eq(RESTAURANT_ID),
                eq(true),
                eq("%breakfast%"),
                eq(false),
                eq(RESTAURANT_ID),
                eq(ACTOR_ID),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(menu)));

        PageResponse<MenuResponse> response = menuService.getMenus(
                authentication,
                RESTAURANT_ID,
                true,
                " Breakfast ",
                0,
                10,
                "name",
                "asc"
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(menuRepository).should().searchVisibleMenus(
                eq(RESTAURANT_ID),
                eq(true),
                eq("%breakfast%"),
                eq(false),
                eq(RESTAURANT_ID),
                eq(ACTOR_ID),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("name").getDirection().name()).isEqualTo("ASC");
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo(MENU_ID);
        assertThat(response.getItems().get(0).getRestaurant().getId()).isEqualTo(RESTAURANT_ID);
    }

    @Test
    @DisplayName("getMenus should reject unsupported sort directions")
    void shouldRejectUnsupportedSortDirection() {
        Authentication authentication = authentication();

        assertThatThrownBy(() -> menuService.getMenus(
                authentication,
                null,
                null,
                null,
                0,
                20,
                "displayOrder",
                "sideways"
        ))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid sort direction");
    }

    @Test
    @DisplayName("createMenu should normalize values and persist audit fields")
    void shouldCreateMenu() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);

        CreateMenuRequest request = CreateMenuRequest.builder()
                .restaurantId(RESTAURANT_ID)
                .code(" lunch specials ")
                .name(" Lunch Specials ")
                .description(" Midday menu ")
                .active(true)
                .displayOrder(2)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(menuRepository.existsByRestaurantIdAndCode(RESTAURANT_ID, "LUNCH_SPECIALS")).willReturn(false);
        given(menuRepository.saveAndFlush(any(Menu.class))).willAnswer(invocation -> {
            Menu saved = invocation.getArgument(0);
            saved.setId(MENU_ID);
            saved.setCreatedAt(OffsetDateTime.parse("2026-04-28T10:15:30Z"));
            saved.setUpdatedAt(saved.getCreatedAt());
            return saved;
        });

        MenuResponse response = menuService.createMenu(authentication, request);

        ArgumentCaptor<Menu> menuCaptor = ArgumentCaptor.forClass(Menu.class);
        verify(menuRepository).saveAndFlush(menuCaptor.capture());
        Menu savedMenu = menuCaptor.getValue();

        assertThat(savedMenu.getRestaurant().getId()).isEqualTo(RESTAURANT_ID);
        assertThat(savedMenu.getCode()).isEqualTo("LUNCH_SPECIALS");
        assertThat(savedMenu.getName()).isEqualTo("Lunch Specials");
        assertThat(savedMenu.getDescription()).isEqualTo("Midday menu");
        assertThat(savedMenu.isActive()).isTrue();
        assertThat(savedMenu.getDisplayOrder()).isEqualTo(2);
        assertThat(savedMenu.getCreatedBy()).isEqualTo(ACTOR_ID);
        assertThat(savedMenu.getUpdatedBy()).isEqualTo(ACTOR_ID);

        assertThat(response.getId()).isEqualTo(MENU_ID);
        assertThat(response.getCode()).isEqualTo("LUNCH_SPECIALS");
        assertThat(response.getCreatedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    @DisplayName("createMenu should reject restaurants still in registration review")
    void shouldRejectCreateForPendingRestaurant() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(RestaurantStatus.PENDING);

        CreateMenuRequest request = CreateMenuRequest.builder()
                .restaurantId(RESTAURANT_ID)
                .name("Breakfast")
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        doThrow(new AuthException(
                "PENDING and REJECTED statuses are reserved for registration review",
                HttpStatus.BAD_REQUEST
        )).when(restaurantValidationService).validateManageableStatus(RestaurantStatus.PENDING);

        assertThatThrownBy(() -> menuService.createMenu(authentication, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("PENDING and REJECTED statuses are reserved for registration review");

        verify(menuRepository, never()).saveAndFlush(any(Menu.class));
    }

    @Test
    @DisplayName("updateMenuStatus should reject archived restaurants")
    void shouldRejectStatusUpdateForArchivedRestaurant() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Restaurant restaurant = restaurant(RestaurantStatus.ARCHIVED);
        Menu menu = menu(restaurant);
        UpdateMenuStatusRequest request = new UpdateMenuStatusRequest();
        request.setActive(false);

        given(actorScopeService.resolve(authentication)).willReturn(scope);
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(java.util.Optional.of(menu));

        assertThatThrownBy(() -> menuService.updateMenuStatus(authentication, MENU_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("Archived restaurants cannot be modified");

        verify(menuRepository, never()).saveAndFlush(any(Menu.class));
    }

    @Test
    @DisplayName("deleteMenu should reject menus that still have sections")
    void shouldRejectDeleteWhenSectionsExist() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Restaurant restaurant = restaurant(RestaurantStatus.ACTIVE);
        Menu menu = menu(restaurant);

        given(actorScopeService.resolve(authentication)).willReturn(scope);
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(java.util.Optional.of(menu));
        given(menuSectionRepository.existsByMenuId(MENU_ID)).willReturn(true);

        assertThatThrownBy(() -> menuService.deleteMenu(authentication, MENU_ID))
                .isInstanceOf(MenuDeletionBlockedException.class)
                .hasMessage("Menu cannot be deleted while it still has sections");

        verify(menuRepository, never()).delete(any(Menu.class));
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

    private Restaurant restaurant(RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");
        restaurant.setCode("POS_MAIN");
        restaurant.setActive(status == RestaurantStatus.ACTIVE);
        restaurant.setStatus(status);
        restaurant.setOwnerId(ACTOR_ID);
        return restaurant;
    }

    private Menu menu(Restaurant restaurant) {
        Menu menu = new Menu();
        menu.setId(MENU_ID);
        menu.setRestaurant(restaurant);
        menu.setCode("BREAKFAST");
        menu.setName("Breakfast");
        menu.setActive(true);
        menu.setDisplayOrder(1);
        return menu;
    }
}
