package pos.pos.unit.menu.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.dto.response.OnlineMenuResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.OnlineMenuSection;
import pos.pos.menu.repository.MenuItemRepository;
import pos.pos.menu.repository.OnlineMenuSectionRepository;
import pos.pos.menu.service.OnlineMenuService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OnlineMenuServiceTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final LocalDate DATE = LocalDate.of(2026, 9, 27);

    @Mock OnlineMenuSectionRepository onlineMenuSectionRepository;
    @Mock MenuItemRepository menuItemRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock RestaurantScopeService restaurantScopeService;
    @Mock Authentication authentication;
    @InjectMocks OnlineMenuService onlineMenuService;

    private final Restaurant restaurant = restaurant();

    @Test void anExistingOnlineSectionWithTheSameNameIsReused() {
        OnlineMenuSection pasta = section("Pasta", 0);
        when(onlineMenuSectionRepository.findFirstByRestaurant_IdAndNameIgnoreCase(RESTAURANT_ID, "pasta")).thenReturn(Optional.of(pasta));

        assertThat(onlineMenuService.resolveSection(restaurant, null, " pasta ")).isSameAs(pasta);
        verify(onlineMenuSectionRepository, never()).save(any());
    }

    @Test void aMissingOnlineSectionIsCreatedAtTheEnd() {
        when(onlineMenuSectionRepository.findFirstByRestaurant_IdAndNameIgnoreCase(RESTAURANT_ID, "Pasta")).thenReturn(Optional.empty());
        when(onlineMenuSectionRepository.findMaxDisplayOrder(RESTAURANT_ID)).thenReturn(2);
        when(onlineMenuSectionRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        OnlineMenuSection created = onlineMenuService.resolveSection(restaurant, null, "Pasta");

        assertThat(created.getName()).isEqualTo("Pasta");
        assertThat(created.getDisplayOrder()).isEqualTo(3);
        assertThat(created.getRestaurant()).isSameAs(restaurant);
    }

    @Test void theWebsiteSeesOnlyVisibleDishesAndNoEmptySections() {
        OnlineMenuSection pasta = section("Pasta", 0);
        OnlineMenuSection desserts = section("Desserts", 1);
        MenuItem carbonara = dish("Carbonara", pasta, true);
        MenuItem tiramisu = dish("Tiramisu", desserts, false);
        when(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(menuItemRepository.findOnlineByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(carbonara, tiramisu));
        when(onlineMenuSectionRepository.findByRestaurant_IdOrderByDisplayOrderAscNameAsc(RESTAURANT_ID)).thenReturn(List.of(pasta, desserts));

        OnlineMenuResponse menu = onlineMenuService.getPublic(RESTAURANT_ID, DATE);

        assertThat(menu.getSections()).extracting(OnlineMenuResponse.Section::getName).containsExactly("Pasta");
        assertThat(menu.getSections().get(0).getItems()).extracting(OnlineMenuResponse.Item::getName).containsExactly("Carbonara");
    }

    @Test void theStaffPreviewAlsoShowsHiddenDishesWithTheReason() {
        OnlineMenuSection desserts = section("Desserts", 0);
        MenuItem tiramisu = dish("Tiramisu", desserts, false);
        when(restaurantScopeService.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(menuItemRepository.findOnlineByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(tiramisu));
        when(onlineMenuSectionRepository.findByRestaurant_IdOrderByDisplayOrderAscNameAsc(RESTAURANT_ID)).thenReturn(List.of(desserts));

        OnlineMenuResponse preview = onlineMenuService.getPreview(authentication, RESTAURANT_ID);

        OnlineMenuResponse.Item item = preview.getSections().get(0).getItems().get(0);
        assertThat(item.getVisible()).isFalse();
        assertThat(item.getHiddenReason()).isEqualTo("Sold out");
    }

    @Test void aSectionWithDishesCannotBeDeleted() {
        OnlineMenuSection pasta = section("Pasta", 0);
        when(onlineMenuSectionRepository.findByIdAndRestaurant_Id(pasta.getId(), RESTAURANT_ID)).thenReturn(Optional.of(pasta));
        when(menuItemRepository.countByOnlineSection_Id(pasta.getId())).thenReturn(2L);

        assertThatThrownBy(() -> onlineMenuService.deleteSection(authentication, RESTAURANT_ID, pasta.getId()))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Move its dishes");
        verify(onlineMenuSectionRepository, never()).delete(any());
    }

    @Test void reorderingMustListEverySectionOnce() {
        OnlineMenuSection pasta = section("Pasta", 0);
        OnlineMenuSection desserts = section("Desserts", 1);
        when(onlineMenuSectionRepository.findByRestaurant_IdOrderByDisplayOrderAscNameAsc(RESTAURANT_ID)).thenReturn(List.of(pasta, desserts));

        assertThatThrownBy(() -> onlineMenuService.reorderSections(authentication, RESTAURANT_ID, List.of(desserts.getId())))
                .isInstanceOf(AuthException.class);
    }

    @Test void aDishNewToASectionGoesLastAndItsOldSectionGoesAwayWhenEmpty() {
        OnlineMenuSection starters = section("Starters", 0);
        OnlineMenuSection picks = section("Chef's picks", 1);
        MenuItem burrata = dish("Burrata", starters, true);
        when(menuItemRepository.findMaxOnlineDisplayOrder(picks.getId())).thenReturn(4);
        when(menuItemRepository.findByOnlineSection_Id(starters.getId())).thenReturn(List.of(burrata));

        onlineMenuService.place(burrata, picks);

        assertThat(burrata.getOnlineSection()).isSameAs(picks);
        assertThat(burrata.getOnlineDisplayOrder()).isEqualTo(5);
        verify(onlineMenuSectionRepository).delete(starters);
    }

    @Test void aSectionThatStillHasDishesStays() {
        OnlineMenuSection starters = section("Starters", 0);
        MenuItem burrata = dish("Burrata", starters, true);
        MenuItem olives = dish("Olives", starters, true);
        when(menuItemRepository.findByOnlineSection_Id(starters.getId())).thenReturn(List.of(burrata, olives));

        onlineMenuService.place(burrata, null);

        assertThat(burrata.isShowOnline()).isFalse();
        verify(onlineMenuSectionRepository, never()).delete(any());
    }

    @Test void dishesInASectionCanBeReordered() {
        OnlineMenuSection pasta = section("Pasta", 0);
        MenuItem carbonara = dish("Carbonara", pasta, true);
        MenuItem amatriciana = dish("Amatriciana", pasta, true);
        when(onlineMenuSectionRepository.findByIdAndRestaurant_Id(pasta.getId(), RESTAURANT_ID)).thenReturn(Optional.of(pasta));
        when(menuItemRepository.findByOnlineSection_Id(pasta.getId())).thenReturn(List.of(carbonara, amatriciana));
        when(restaurantScopeService.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);

        onlineMenuService.reorderSectionItems(authentication, RESTAURANT_ID, pasta.getId(), List.of(amatriciana.getId(), carbonara.getId()));

        assertThat(amatriciana.getOnlineDisplayOrder()).isZero();
        assertThat(carbonara.getOnlineDisplayOrder()).isEqualTo(1);
    }

    private static Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setTimezone("Europe/Rome");
        restaurant.setActive(true);
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        return restaurant;
    }

    private OnlineMenuSection section(String name, int order) {
        OnlineMenuSection section = new OnlineMenuSection();
        section.setId(UUID.randomUUID());
        section.setRestaurant(restaurant);
        section.setName(name);
        section.setDisplayOrder(order);
        return section;
    }

    private static MenuItem dish(String name, OnlineMenuSection onlineSection, boolean available) {
        Menu menu = new Menu();
        menu.setId(UUID.randomUUID());
        menu.setName("Dinner");
        menu.setActive(true);
        MenuSection section = new MenuSection();
        section.setId(UUID.randomUUID());
        section.setName(onlineSection.getName());
        section.setActive(true);
        section.setMenu(menu);
        MenuItem item = new MenuItem();
        item.setId(UUID.randomUUID());
        item.setName(name);
        item.setBasePrice(new BigDecimal("9.00"));
        item.setAvailable(available);
        item.setSection(section);
        item.setOnlineSection(onlineSection);
        return item;
    }
}
