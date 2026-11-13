package pos.pos.unit.menu.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.menu.service.SpecialMenuEventLinks;
import pos.pos.reservation.entity.RestaurantEvent;
import pos.pos.reservation.repository.RestaurantEventRepository;
import pos.pos.restaurant.entity.Restaurant;

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

// The menu editor's side of the special menu ↔ event night link.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Special menu and event night link")
class SpecialMenuEventLinksTest {

    @Mock RestaurantEventRepository eventRepository;
    @Mock MenuRepository menuRepository;

    private final Restaurant restaurant = new Restaurant();
    private SpecialMenuEventLinks links;

    @BeforeEach
    void setUp() {
        restaurant.setId(UUID.randomUUID());
        links = new SpecialMenuEventLinks(eventRepository, menuRepository);
        when(eventRepository.saveAndFlush(any(RestaurantEvent.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("a menu joining a free event becomes special and takes the event's days")
    void joinFreeEvent() {
        Menu menu = menu("Valentine's menu");
        RestaurantEvent valentines = event("Valentine's", LocalDate.of(2027, 2, 13), LocalDate.of(2027, 2, 14));

        links.menuJoinsEvent(menu, valentines.getId());

        assertThat(valentines.getMenuId()).isEqualTo(menu.getId());
        assertThat(menu.isSpecial()).isTrue();
        assertThat(menu.getAvailableFromDate()).isEqualTo(LocalDate.of(2027, 2, 13));
        assertThat(menu.getAvailableUntilDate()).isEqualTo(LocalDate.of(2027, 2, 14));
    }

    @Test
    @DisplayName("an event that already has another menu is refused, naming the event and that menu")
    void eventTaken() {
        Menu dinner = menu("Dinner menu");
        Menu menu = menu("Valentine's menu");
        RestaurantEvent valentines = event("Valentine's", LocalDate.of(2027, 2, 14), LocalDate.of(2027, 2, 14));
        valentines.setMenuId(dinner.getId());

        assertThatThrownBy(() -> links.menuJoinsEvent(menu, valentines.getId()))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Valentine's already uses the menu \"Dinner menu\"");
        assertThat(valentines.getMenuId()).isEqualTo(dinner.getId());
        verify(eventRepository, never()).saveAndFlush(any(RestaurantEvent.class));
    }

    @Test
    @DisplayName("an event of another restaurant can't be picked")
    void otherRestaurantsEvent() {
        Menu menu = menu("Valentine's menu");

        assertThatThrownBy(() -> links.menuJoinsEvent(menu, UUID.randomUUID()))
                .hasMessageContaining("Choose one of this restaurant's events");
    }

    @Test
    @DisplayName("moving a menu to another event: the old event lets go of it (one event per menu)")
    void moveToAnotherEvent() {
        Menu menu = menu("Party menu");
        RestaurantEvent newYear = event("New Year's Eve", LocalDate.of(2026, 12, 31), LocalDate.of(2026, 12, 31));
        newYear.setMenuId(menu.getId());
        RestaurantEvent carnival = event("Carnival", LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 2));
        when(eventRepository.findFirstByMenuId(menu.getId())).thenReturn(Optional.of(newYear));

        links.menuJoinsEvent(menu, carnival.getId());

        assertThat(newYear.getMenuId()).isNull();
        assertThat(carnival.getMenuId()).isEqualTo(menu.getId());
        assertThat(menu.getAvailableFromDate()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    @DisplayName("a menu that stops being an event menu frees its event")
    void leaveEvent() {
        Menu menu = menu("Valentine's menu");
        RestaurantEvent valentines = event("Valentine's", LocalDate.of(2027, 2, 14), LocalDate.of(2027, 2, 14));
        valentines.setMenuId(menu.getId());
        when(eventRepository.findFirstByMenuId(menu.getId())).thenReturn(Optional.of(valentines));

        links.menuLeavesEvent(menu);

        assertThat(valentines.getMenuId()).isNull();
    }

    @Test
    @DisplayName("an edit can't move an event menu's days away from its event")
    void keepEventDays() {
        Menu menu = menu("Valentine's menu");
        menu.setAvailableFromDate(LocalDate.of(2027, 6, 1));
        menu.setAvailableUntilDate(LocalDate.of(2027, 6, 30));
        RestaurantEvent valentines = event("Valentine's", LocalDate.of(2027, 2, 14), LocalDate.of(2027, 2, 14));
        valentines.setMenuId(menu.getId());
        when(eventRepository.findFirstByMenuId(menu.getId())).thenReturn(Optional.of(valentines));

        links.keepEventDays(menu);

        assertThat(menu.getAvailableFromDate()).isEqualTo(LocalDate.of(2027, 2, 14));
        assertThat(menu.getAvailableUntilDate()).isEqualTo(LocalDate.of(2027, 2, 14));
    }

    @Test
    @DisplayName("menus are matched to their events in one look-up")
    void eventsOfMenus() {
        Menu menu = menu("Valentine's menu");
        RestaurantEvent valentines = event("Valentine's", LocalDate.of(2027, 2, 14), LocalDate.of(2027, 2, 14));
        valentines.setMenuId(menu.getId());
        when(eventRepository.findAllByMenuIdIn(List.of(menu.getId()))).thenReturn(List.of(valentines));

        assertThat(links.eventsOf(List.of(menu.getId()))).containsEntry(menu.getId(), valentines);
        assertThat(links.eventsOf(List.of())).isEmpty();
    }

    private Menu menu(String name) {
        Menu menu = new Menu();
        menu.setId(UUID.randomUUID());
        menu.setName(name);
        menu.setRestaurant(restaurant);
        menu.setActive(true);
        when(menuRepository.findById(menu.getId())).thenReturn(Optional.of(menu));
        return menu;
    }

    private RestaurantEvent event(String name, LocalDate start, LocalDate end) {
        RestaurantEvent event = new RestaurantEvent();
        event.setId(UUID.randomUUID());
        event.setRestaurantId(restaurant.getId());
        event.setName(name);
        event.setIcon("🎉");
        event.setStartDate(start);
        event.setEndDate(end);
        when(eventRepository.findByIdAndRestaurantId(event.getId(), restaurant.getId())).thenReturn(Optional.of(event));
        return event;
    }
}
