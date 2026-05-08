package pos.pos.unit.menu.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.exception.menu.MenuNotFoundException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.menu.dto.PublicMenuResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.mapper.PublicMenuMapper;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.repository.MenuSectionRepository;
import pos.pos.menu.service.PublicMenuService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PublicMenuService")
class PublicMenuServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID SECTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000203");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000204");

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private MenuSectionRepository menuSectionRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Spy
    private PublicMenuMapper publicMenuMapper = new PublicMenuMapper();

    @InjectMocks
    private PublicMenuService publicMenuService;

    @Test
    @DisplayName("getMenus should return mapped active menus for an active restaurant")
    void shouldReturnPublicMenus() {
        Restaurant restaurant = restaurant(true, RestaurantStatus.ACTIVE);
        Menu menu = menu();

        given(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(Optional.of(restaurant));
        given(menuRepository.findPublicMenusByRestaurantId(RESTAURANT_ID)).willReturn(List.of(menu));

        List<PublicMenuResponse> response = publicMenuService.getMenus(RESTAURANT_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getId()).isEqualTo(MENU_ID);
        assertThat(response.get(0).getCode()).isEqualTo("BREAKFAST");
        verify(menuRepository).findPublicMenusByRestaurantId(RESTAURANT_ID);
    }

    @Test
    @DisplayName("getMenu should return expanded active sections and items")
    void shouldReturnExpandedPublicMenu() {
        Restaurant restaurant = restaurant(true, RestaurantStatus.ACTIVE);
        Menu menu = menu();
        MenuSection section = section(menu);
        MenuItem item = item(section);

        given(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(Optional.of(restaurant));
        given(menuRepository.findPublicMenuByRestaurantIdAndId(RESTAURANT_ID, MENU_ID)).willReturn(Optional.of(menu));
        given(menuSectionRepository.findByMenuIdAndActiveTrueOrderByDisplayOrderAscNameAsc(MENU_ID)).willReturn(List.of(section));
        given(menuItemRepository.findByMenuIdAndAvailableTrueOrdered(MENU_ID)).willReturn(List.of(item));

        PublicMenuResponse response = publicMenuService.getMenu(RESTAURANT_ID, MENU_ID, true, true);

        assertThat(response.getSections()).hasSize(1);
        assertThat(response.getSections().get(0).getId()).isEqualTo(SECTION_ID);
        assertThat(response.getSections().get(0).getItems()).hasSize(1);
        assertThat(response.getSections().get(0).getItems().get(0).getId()).isEqualTo(ITEM_ID);
    }

    @Test
    @DisplayName("getMenus should reject restaurants that are not publicly active")
    void shouldRejectInactiveRestaurant() {
        given(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID))
                .willReturn(Optional.of(restaurant(false, RestaurantStatus.INACTIVE)));

        assertThatThrownBy(() -> publicMenuService.getMenus(RESTAURANT_ID))
                .isInstanceOf(RestaurantNotFoundException.class)
                .hasMessage("Restaurant not found");
    }

    @Test
    @DisplayName("getMenu should reject missing public menus")
    void shouldRejectMissingPublicMenu() {
        given(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID))
                .willReturn(Optional.of(restaurant(true, RestaurantStatus.ACTIVE)));
        given(menuRepository.findPublicMenuByRestaurantIdAndId(RESTAURANT_ID, MENU_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> publicMenuService.getMenu(RESTAURANT_ID, MENU_ID, true, true))
                .isInstanceOf(MenuNotFoundException.class)
                .hasMessage("Menu not found");
    }

    private Restaurant restaurant(boolean active, RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");
        restaurant.setActive(active);
        restaurant.setStatus(status);
        return restaurant;
    }

    private Menu menu() {
        Menu menu = new Menu();
        menu.setId(MENU_ID);
        menu.setCode("BREAKFAST");
        menu.setName("Breakfast");
        menu.setDescription("Morning menu");
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
        item.setAvailable(true);
        item.setDisplayOrder(1);
        return item;
    }
}
