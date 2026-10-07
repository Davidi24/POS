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
import pos.pos.exception.menu.MenuItemDeletionBlockedException;
import pos.pos.exception.menu.MenuItemSectionMismatchException;
import pos.pos.menu.dto.request.CreateMenuItemRequest;
import pos.pos.menu.dto.update.UpdateMenuItemRequest;
import pos.pos.menu.dto.response.MenuItemSummaryResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.menu.mapper.MenuMapper;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.menu.repository.MenuItemOptionGroupRepository;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.repository.MenuVariantRepository;
import pos.pos.menu.service.MenuItemService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MenuItemService")
class MenuItemServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000401");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000402");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000403");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000404");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000405");
    private static final UUID FOREIGN_SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000406");

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
    private pos.pos.menu.service.OnlineMenuService onlineMenuService;

    private final MenuMapper menuMapper = new MenuMapper();
    private final MenuPolicy menuPolicy = new MenuPolicy(new RestaurantPolicy());
    private MenuItemService menuItemService;

    private StubActorScopeService actorScopeService;
    private StubRestaurantValidationService restaurantValidationService;

    @BeforeEach
    void setUp() {
        actorScopeService = new StubActorScopeService();
        restaurantValidationService = new StubRestaurantValidationService();
        menuItemService = new MenuItemService(
                menuRepository,
                menuSectionRepository,
                menuItemRepository,
                menuVariantRepository,
                menuItemOptionGroupRepository,
                menuMapper,
                actorScopeService,
                menuPolicy,
                restaurantValidationService,
                onlineMenuService
        );
        // Placement itself is covered in OnlineMenuServiceTest; here it just records where the dish went.
        org.mockito.Mockito.lenient().doAnswer(call -> {
            ((MenuItem) call.getArgument(0)).setOnlineSection(call.getArgument(1));
            return null;
        }).when(onlineMenuService).place(any(), any());
    }

    @Test
    @DisplayName("getItems should apply the availability filter and include expansions when requested")
    void shouldReturnFilteredItems() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuItem item = item(section);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findBySectionIdAndAvailableOrderByDisplayOrderAscNameAsc(SECTION_ID, true)).willReturn(List.of(item));
        given(menuVariantRepository.findByMenuItemIdInOrdered(List.of(ITEM_ID))).willReturn(List.of(variant(item)));
        given(menuItemOptionGroupRepository.findByMenuItemIdInOrdered(List.of(ITEM_ID))).willReturn(List.of(optionGroupLink(item)));

        List<MenuItemSummaryResponse> response = menuItemService.getItems(authentication, MENU_ID, SECTION_ID, true, true, true);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(ITEM_ID);
        assertThat(response.get(0).getVariants()).hasSize(1);
        assertThat(response.get(0).getOptionGroups()).hasSize(1);
        verify(menuVariantRepository).findByMenuItemIdInOrdered(List.of(ITEM_ID));
        verify(menuItemOptionGroupRepository).findByMenuItemIdInOrdered(List.of(ITEM_ID));
        verify(menuVariantRepository, never()).findByMenuItemIdOrderByDisplayOrderAscNameAsc(ITEM_ID);
        verify(menuItemOptionGroupRepository, never()).findByMenuItemIdOrdered(ITEM_ID);
    }

    @Test
    @DisplayName("createItem should normalize values and persist defaults")
    void shouldCreateItem() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);

        CreateMenuItemRequest request = CreateMenuItemRequest.builder()
                .sku(" brg-001 ")
                .name(" House Burger ")
                .description(" Signature burger ")
                .basePrice(new BigDecimal("12.50"))
                .imageUrl(" https://img.example/burger ")
                .build();

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.saveAndFlush(any(MenuItem.class))).willAnswer(invocation -> {
            MenuItem saved = invocation.getArgument(0);
            saved.setId(ITEM_ID);
            return saved;
        });

        MenuItemSummaryResponse response = menuItemService.createItem(authentication, MENU_ID, SECTION_ID, request);

        ArgumentCaptor<MenuItem> captor = ArgumentCaptor.forClass(MenuItem.class);
        verify(menuItemRepository).saveAndFlush(captor.capture());
        MenuItem saved = captor.getValue();

        assertThat(saved.getSection().getId()).isEqualTo(SECTION_ID);
        assertThat(saved.getSku()).isEqualTo("BRG-001");
        assertThat(saved.getName()).isEqualTo("House Burger");
        assertThat(saved.getDescription()).isEqualTo("Signature burger");
        assertThat(saved.getImageUrl()).isEqualTo("https://img.example/burger");
        assertThat(saved.isAvailable()).isTrue();
        assertThat(saved.isSendToKitchen()).isTrue();
        // New dishes stay on the staff menu until someone switches them on for online.
        assertThat(saved.isShowOnline()).isFalse();
        assertThat(saved.getDisplayOrder()).isZero();
        assertThat(response.getId()).isEqualTo(ITEM_ID);
        assertThat(response.getSendToKitchen()).isTrue();
    }

    @Test
    @DisplayName("createItem should keep counter items like a cola away from the kitchen")
    void shouldCreateCounterItem() {
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        CreateMenuItemRequest request = CreateMenuItemRequest.builder()
                .name("Cola")
                .basePrice(new BigDecimal("2.50"))
                .sendToKitchen(false)
                .build();

        actorScopeService.scope = actorScope(false, RESTAURANT_ID);
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.saveAndFlush(any(MenuItem.class))).willAnswer(invocation -> invocation.getArgument(0));

        MenuItemSummaryResponse response = menuItemService.createItem(authentication(), MENU_ID, SECTION_ID, request);

        assertThat(response.getSendToKitchen()).isFalse();
    }

    @Test
    @DisplayName("updateItem should keep sendToKitchen when an older client leaves it out, and change it when sent")
    void shouldKeepOrChangeSendToKitchenOnUpdate() {
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuItem existing = item(section);
        existing.setSendToKitchen(false);
        UpdateMenuItemRequest.UpdateMenuItemRequestBuilder request = UpdateMenuItemRequest.builder()
                .name("Cola")
                .basePrice(new BigDecimal("2.50"))
                .available(true)
                .displayOrder(1);

        actorScopeService.scope = actorScope(false, RESTAURANT_ID);
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(existing));
        given(menuItemRepository.saveAndFlush(any(MenuItem.class))).willAnswer(invocation -> invocation.getArgument(0));

        existing.setOnlineSection(new pos.pos.menu.entity.OnlineMenuSection());
        menuItemService.updateItem(authentication(), MENU_ID, SECTION_ID, ITEM_ID, request.build());
        assertThat(existing.isSendToKitchen()).isFalse();
        assertThat(existing.isShowOnline()).isTrue();

        menuItemService.updateItem(authentication(), MENU_ID, SECTION_ID, ITEM_ID, request.sendToKitchen(true).showOnline(false).build());
        assertThat(existing.isSendToKitchen()).isTrue();
        assertThat(existing.isShowOnline()).isFalse();
    }

    @Test
    @DisplayName("getItem should reject items that belong to another section")
    void shouldRejectMismatchedItem() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuSection foreignSection = section(menu);
        foreignSection.setId(FOREIGN_SECTION_ID);
        MenuItem foreignItem = item(foreignSection);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(foreignItem));

        assertThatThrownBy(() -> menuItemService.getItem(authentication, MENU_ID, SECTION_ID, ITEM_ID, false, false))
                .isInstanceOf(MenuItemSectionMismatchException.class)
                .hasMessage("Menu item does not belong to this section");
    }

    @Test
    @DisplayName("deleteItem should reject items that still have variants or option groups")
    void shouldRejectDeleteWhenDependentsExist() {
        Authentication authentication = authentication();
        ActorScope scope = actorScope(false, RESTAURANT_ID);
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuItem item = item(section);

        actorScopeService.scope = scope;
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(item));
        given(menuVariantRepository.existsByMenuItemId(ITEM_ID)).willReturn(true);

        assertThatThrownBy(() -> menuItemService.deleteItem(authentication, MENU_ID, SECTION_ID, ITEM_ID))
                .isInstanceOf(MenuItemDeletionBlockedException.class)
                .hasMessage("Menu item cannot be deleted while it still has variants or option groups");

        verify(menuItemRepository, never()).delete(any(MenuItem.class));
    }

    @Test
    @DisplayName("createItem with Show in online menu should place the dish in the online section named like its own section")
    void shouldPlaceNewDishInSameNamedOnlineSection() {
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        pos.pos.menu.entity.OnlineMenuSection onlinePasta = new pos.pos.menu.entity.OnlineMenuSection();
        onlinePasta.setName(section.getName());

        actorScopeService.scope = actorScope(false, RESTAURANT_ID);
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(onlineMenuService.resolveSection(menu.getRestaurant(), null, section.getName())).willReturn(onlinePasta);
        given(menuItemRepository.saveAndFlush(any(MenuItem.class))).willAnswer(invocation -> invocation.getArgument(0));

        MenuItemSummaryResponse response = menuItemService.createItem(authentication(), MENU_ID, SECTION_ID, CreateMenuItemRequest.builder()
                .name("Carbonara")
                .basePrice(new BigDecimal("14.00"))
                .showOnline(true)
                .build());

        assertThat(response.getShowOnline()).isTrue();
        assertThat(response.getOnlineSectionName()).isEqualTo(section.getName());
    }

    @Test
    @DisplayName("updateItem with a custom online section name should use that section")
    void shouldMoveDishToCustomOnlineSection() {
        Menu menu = menu(RestaurantStatus.ACTIVE);
        MenuSection section = section(menu);
        MenuItem existing = item(section);
        pos.pos.menu.entity.OnlineMenuSection favourites = new pos.pos.menu.entity.OnlineMenuSection();
        favourites.setName("Chef's favourites");

        actorScopeService.scope = actorScope(false, RESTAURANT_ID);
        given(menuRepository.findByIdAndRestaurantDeletedAtIsNull(MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findById(SECTION_ID)).willReturn(Optional.of(section));
        given(menuItemRepository.findById(ITEM_ID)).willReturn(Optional.of(existing));
        given(onlineMenuService.resolveSection(menu.getRestaurant(), null, "Chef's favourites")).willReturn(favourites);
        given(menuItemRepository.saveAndFlush(any(MenuItem.class))).willAnswer(invocation -> invocation.getArgument(0));

        menuItemService.updateItem(authentication(), MENU_ID, SECTION_ID, ITEM_ID, UpdateMenuItemRequest.builder()
                .name("House Burger")
                .basePrice(new BigDecimal("12.50"))
                .available(true)
                .displayOrder(1)
                .onlineSectionName("Chef's favourites")
                .build());

        assertThat(existing.getOnlineSection()).isSameAs(favourites);
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
        item.setDescription("Signature burger");
        item.setBasePrice(new BigDecimal("12.50"));
        item.setImageUrl("https://img.example/burger");
        item.setAvailable(true);
        item.setDisplayOrder(1);
        return item;
    }

    private MenuVariant variant(MenuItem item) {
        MenuVariant variant = new MenuVariant();
        variant.setId(UUID.fromString("00000000-0000-0000-0000-000000000407"));
        variant.setMenuItem(item);
        variant.setName("Large");
        variant.setSku("BRG-L");
        variant.setPriceDelta(new BigDecimal("2.00"));
        variant.setDefault(false);
        variant.setActive(true);
        variant.setDisplayOrder(1);
        return variant;
    }

    private MenuItemOptionGroup optionGroupLink(MenuItem item) {
        OptionGroup optionGroup = new OptionGroup();
        optionGroup.setId(UUID.fromString("00000000-0000-0000-0000-000000000408"));
        optionGroup.setName("Sauces");
        optionGroup.setDescription("Choose a sauce");
        optionGroup.setActive(true);
        optionGroup.setMinSelect(0);
        optionGroup.setMaxSelect(2);
        optionGroup.setRequired(false);

        MenuItemOptionGroup link = new MenuItemOptionGroup();
        link.setId(UUID.fromString("00000000-0000-0000-0000-000000000409"));
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
