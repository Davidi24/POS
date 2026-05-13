package pos.pos.unit.menu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.menu.MenuVariantItemMismatchException;
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
import pos.pos.menu.service.MenuVariantService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
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
@DisplayName("MenuVariantService")
class MenuVariantServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000441");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000442");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000443");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000444");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000445");
    private static final UUID VARIANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000446");
    private static final UUID FOREIGN_ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000447");

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private MenuSectionRepository menuSectionRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private MenuVariantRepository menuVariantRepository;

    private final MenuMapper menuMapper = new MenuMapper();
    private final MenuPolicy menuPolicy = new MenuPolicy(new RestaurantPolicy());
    private MenuVariantService menuVariantService;

    private StubActorScopeService actorScopeService;
    private StubRestaurantValidationService restaurantValidationService;

    @BeforeEach
    void setUp() {
        actorScopeService = new StubActorScopeService();
        restaurantValidationService = new StubRestaurantValidationService();
        menuVariantService = new MenuVariantService(
                menuRepository,
                menuSectionRepository,
                menuItemRepository,
                menuVariantRepository,
                menuMapper,
                actorScopeService,
                menuPolicy,
                restaurantValidationService
        );
    }

    @Test
    @DisplayName("getVariants should return variants for the scoped item")
    void shouldReturnVariants() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuItem item = item(section);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(menuVariantRepository.findByMenuItemIdOrderByDisplayOrderAscNameAsc(ITEM_ID)).willReturn(List.of(variant(item, VARIANT_ID, true)));

        List<MenuVariantSummaryResponse> response = menuVariantService.getVariants(authentication, MENU_ID, SECTION_ID, ITEM_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(VARIANT_ID);
        assertThat(response.get(0).getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("createVariant should normalize values, persist defaults, and clear other default variants")
    void shouldCreateVariantAndClearOtherDefaults() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuItem item = item(section);
        MenuVariant existingDefault = variant(item, UUID.fromString("00000000-0000-0000-0000-000000000448"), true);

        CreateMenuVariantRequest request = CreateMenuVariantRequest.builder()
                .name(" Large ")
                .sku(" brg-l ")
                .priceDelta(new BigDecimal("2.50"))
                .isDefault(true)
                .build();

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(menuVariantRepository.existsByMenuItemIdAndName(ITEM_ID, "Large")).willReturn(false);
        given(menuVariantRepository.findByMenuItemIdOrderByDisplayOrderAscNameAsc(ITEM_ID)).willReturn(List.of(existingDefault));
        given(menuVariantRepository.saveAndFlush(any(MenuVariant.class))).willAnswer(invocation -> {
            MenuVariant saved = invocation.getArgument(0);
            saved.setId(VARIANT_ID);
            return saved;
        });

        MenuVariantSummaryResponse response = menuVariantService.createVariant(authentication, MENU_ID, SECTION_ID, ITEM_ID, request);

        ArgumentCaptor<MenuVariant> captor = ArgumentCaptor.forClass(MenuVariant.class);
        verify(menuVariantRepository).saveAndFlush(captor.capture());
        MenuVariant saved = captor.getValue();

        assertThat(saved.getMenuItem().getId()).isEqualTo(ITEM_ID);
        assertThat(saved.getName()).isEqualTo("Large");
        assertThat(saved.getSku()).isEqualTo("BRG-L");
        assertThat(saved.isDefault()).isTrue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getDisplayOrder()).isZero();
        assertThat(existingDefault.isDefault()).isFalse();
        assertThat(response.getId()).isEqualTo(VARIANT_ID);
    }

    @Test
    @DisplayName("updateVariant should reject variants that belong to another item")
    void shouldRejectMismatchedVariant() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuItem item = item(section);
        MenuItem foreignItem = item(section);
        foreignItem.setId(FOREIGN_ITEM_ID);
        MenuVariant foreignVariant = variant(foreignItem, VARIANT_ID, false);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(menuVariantRepository.findById(VARIANT_ID)).willReturn(Optional.of(foreignVariant));

        assertThatThrownBy(() -> menuVariantService.updateVariant(authentication, MENU_ID, SECTION_ID, ITEM_ID, VARIANT_ID,
                UpdateMenuVariantRequest.builder()
                        .name("Large")
                        .isDefault(false)
                        .active(true)
                        .displayOrder(1)
                        .build()))
                .isInstanceOf(MenuVariantItemMismatchException.class)
                .hasMessage("Menu variant does not belong to this item");
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
        item.setId(ITEM_ID);
        item.setSection(section);
        item.setSku("BRG-001");
        item.setName("House Burger");
        item.setBasePrice(new BigDecimal("12.50"));
        item.setAvailable(true);
        item.setDisplayOrder(1);
        return item;
    }

    private MenuVariant variant(MenuItem item, UUID variantId, boolean isDefault) {
        MenuVariant variant = new MenuVariant();
        variant.setId(variantId);
        variant.setMenuItem(item);
        variant.setName("Small");
        variant.setSku("BRG-S");
        variant.setPriceDelta(BigDecimal.ZERO);
        variant.setDefault(isDefault);
        variant.setActive(true);
        variant.setDisplayOrder(1);
        return variant;
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
