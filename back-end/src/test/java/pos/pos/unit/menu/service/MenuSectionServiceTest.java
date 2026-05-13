package pos.pos.unit.menu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.menu.MenuSectionDeletionBlockedException;
import pos.pos.exception.menu.MenuSectionMenuMismatchException;
import pos.pos.menu.dto.CreateMenuSectionRequest;
import pos.pos.menu.dto.MenuSectionSummaryResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.service.MenuSectionService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuSectionService")
class MenuSectionServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000381");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000382");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000383");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000384");
    private static final UUID FOREIGN_MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000385");

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private MenuSectionRepository menuSectionRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    private MenuMapper menuMapper = new MenuMapper();

    private MenuPolicy menuPolicy = new MenuPolicy(new RestaurantPolicy());

    private MenuSectionService menuSectionService;

    private StubActorScopeService actorScopeService;
    private StubRestaurantValidationService restaurantValidationService;

    @BeforeEach
    void setUp() {
        actorScopeService = new StubActorScopeService();
        restaurantValidationService = new StubRestaurantValidationService();
        menuSectionService = new MenuSectionService(
                menuRepository,
                menuSectionRepository,
                menuItemRepository,
                menuMapper,
                actorScopeService,
                menuPolicy,
                restaurantValidationService
        );
    }

    @Test
    @DisplayName("getSections should apply the active filter and include nested items when requested")
    void shouldReturnFilteredSections() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuItem item = item(section);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findByMenuIdAndActiveOrderByDisplayOrderAscNameAsc(MENU_ID, true)).willReturn(List.of(section));
        given(menuItemRepository.findByMenuIdOrdered(MENU_ID)).willReturn(List.of(item));

        List<MenuSectionSummaryResponse> response = menuSectionService.getSections(authentication, MENU_ID, true, true);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(SECTION_ID);
        assertThat(response.get(0).getItems()).hasSize(1);
        assertThat(response.get(0).getItems().get(0).getName()).isEqualTo("House Burger");
    }

    @Test
    @DisplayName("createSection should normalize values and persist defaults")
    void shouldCreateSection() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);

        CreateMenuSectionRequest request = CreateMenuSectionRequest.builder()
                .name(" Mains ")
                .description(" Main dishes ")
                .build();

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.existsByMenuIdAndName(MENU_ID, "Mains")).willReturn(false);
        given(menuSectionRepository.saveAndFlush(any(MenuSection.class))).willAnswer(invocation -> {
            MenuSection saved = invocation.getArgument(0);
            saved.setId(SECTION_ID);
            return saved;
        });

        MenuSectionSummaryResponse response = menuSectionService.createSection(authentication, MENU_ID, request);

        ArgumentCaptor<MenuSection> captor = ArgumentCaptor.forClass(MenuSection.class);
        verify(menuSectionRepository).saveAndFlush(captor.capture());
        MenuSection saved = captor.getValue();

        assertThat(saved.getMenu().getId()).isEqualTo(MENU_ID);
        assertThat(saved.getName()).isEqualTo("Mains");
        assertThat(saved.getDescription()).isEqualTo("Main dishes");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getDisplayOrder()).isZero();
        assertThat(response.getId()).isEqualTo(SECTION_ID);
    }

    @Test
    @DisplayName("getSection should reject sections that belong to another menu")
    void shouldRejectMismatchedSection() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection foreignSection = section(menu(RestaurantStatus.ACTIVE));
        foreignSection.setId(SECTION_ID);
        foreignSection.getMenu().setId(FOREIGN_MENU_ID);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(foreignSection));

        assertThatThrownBy(() -> menuSectionService.getSection(authentication, MENU_ID, SECTION_ID, false))
                .isInstanceOf(MenuSectionMenuMismatchException.class)
                .hasMessage("Menu section does not belong to this menu");
    }

    @Test
    @DisplayName("deleteSection should reject sections that still have items")
    void shouldRejectDeleteWhenItemsExist() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.existsBySectionId(SECTION_ID)).willReturn(true);

        assertThatThrownBy(() -> menuSectionService.deleteSection(authentication, MENU_ID, SECTION_ID))
                .isInstanceOf(MenuSectionDeletionBlockedException.class)
                .hasMessage("Menu section cannot be deleted while it still has items");

        verify(menuSectionRepository, never()).delete(any(MenuSection.class));
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

    private Menu menu(RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");
        restaurant.setCode("POS_MAIN");
        restaurant.setActive(status == RestaurantStatus.ACTIVE);
        restaurant.setStatus(status);
        restaurant.setOwnerId(ACTOR_ID);

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
        item.setId(UUID.fromString("00000000-0000-0000-0000-000000000386"));
        item.setSection(section);
        item.setSku("BRG-001");
        item.setName("House Burger");
        item.setDescription("House Burger description");
        item.setBasePrice(java.math.BigDecimal.TEN);
        item.setAvailable(true);
        item.setDisplayOrder(1);
        return item;
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
