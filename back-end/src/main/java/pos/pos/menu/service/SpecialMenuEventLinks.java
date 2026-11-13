package pos.pos.menu.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.reservation.entity.RestaurantEvent;
import pos.pos.reservation.repository.RestaurantEventRepository;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// The one link between a special menu and its event night (restaurant_events.menu_id). Admin Hub → Settings →
// Occasions & events and the menu editor both change it through here, so they always agree:
// - a menu belongs to at most one event, and an event has at most one menu;
// - the event owns its menu's days (the menu's dates copy the event's);
// - a menu an event lets go of (the event is removed or gets another menu) is hidden in POS and stops being special,
//   so a Valentine's menu doesn't suddenly show at the till every day (unless it's the last menu shown in POS).
// Special menus with an event are event menus; special menus without one are the booking extras (cakes, flowers…).
@Component
@RequiredArgsConstructor
public class SpecialMenuEventLinks {

    private final RestaurantEventRepository eventRepository;
    private final MenuRepository menuRepository;

    // From the event screen: the event now uses this menu (null: no menu). Call before saving the event.
    public void eventUsesMenu(RestaurantEvent event, Menu menu) {
        UUID previous = event.getMenuId();
        if (menu == null) {
            event.setMenuId(null);
        } else {
            eventRepository.findFirstByMenuId(menu.getId())
                    .filter(other -> !Objects.equals(other.getId(), event.getId()))
                    .ifPresent(other -> {
                        throw new AuthException("\"" + menu.getName() + "\" is already the menu for " + other.getName()
                                + ". Pick another menu, or take it off that event first.", HttpStatus.CONFLICT);
                    });
            event.setMenuId(menu.getId());
            followEvent(menu, event);
        }
        if (previous != null && !previous.equals(event.getMenuId())) {
            release(previous);
        }
    }

    // The event is being removed: its menu is let go of.
    public void eventRemoved(RestaurantEvent event) {
        if (event.getMenuId() != null) {
            release(event.getMenuId());
            event.setMenuId(null);
        }
    }

    // From the menu editor: the (saved) menu is now the menu of this event, taking its days.
    public RestaurantEvent menuJoinsEvent(Menu menu, UUID eventId) {
        RestaurantEvent event = eventRepository.findByIdAndRestaurantId(eventId, menu.getRestaurant().getId())
                .orElseThrow(() -> new AuthException("Choose one of this restaurant's events", HttpStatus.BAD_REQUEST));
        if (event.getMenuId() != null && !event.getMenuId().equals(menu.getId())) {
            String current = menuRepository.findById(event.getMenuId()).map(Menu::getName).orElse("another menu");
            throw new AuthException(event.getName() + " already uses the menu \"" + current
                    + "\". Choose another event, or take that menu off the event first.", HttpStatus.CONFLICT);
        }
        // Moving to another event: the old one lets go first (one event per menu).
        eventRepository.findFirstByMenuId(menu.getId())
                .filter(other -> !other.getId().equals(event.getId()))
                .ifPresent(other -> {
                    other.setMenuId(null);
                    eventRepository.saveAndFlush(other);
                });
        event.setMenuId(menu.getId());
        eventRepository.saveAndFlush(event);
        followEvent(menu, event);
        return event;
    }

    // From the menu editor: the menu is no longer an event menu (a regular menu or booking extras now).
    public void menuLeavesEvent(Menu menu) {
        eventRepository.findFirstByMenuId(menu.getId()).ifPresent(event -> {
            event.setMenuId(null);
            eventRepository.saveAndFlush(event);
        });
    }

    // A still-linked menu keeps its event's days, whatever dates an edit sent.
    public void keepEventDays(Menu menu) {
        eventRepository.findFirstByMenuId(menu.getId()).ifPresent(event -> followEvent(menu, event));
    }

    // The event night on a service day, if one is on.
    public Optional<RestaurantEvent> activeEventOn(UUID restaurantId, java.time.LocalDate serviceDate) {
        return eventRepository.findActiveBetween(restaurantId, serviceDate, serviceDate).stream().findFirst();
    }

    public Optional<RestaurantEvent> eventOf(UUID menuId) {
        return menuId == null ? Optional.empty() : eventRepository.findFirstByMenuId(menuId);
    }

    public Map<UUID, RestaurantEvent> eventsOf(Collection<UUID> menuIds) {
        if (menuIds == null || menuIds.isEmpty()) {
            return Map.of();
        }
        return eventRepository.findAllByMenuIdIn(menuIds).stream()
                .collect(Collectors.toMap(RestaurantEvent::getMenuId, Function.identity(), (first, second) -> first));
    }

    private void followEvent(Menu menu, RestaurantEvent event) {
        menu.setSpecial(true);
        menu.setAvailableFromDate(event.getStartDate());
        menu.setAvailableUntilDate(event.getEndDate());
        menuRepository.save(menu);
    }

    private void release(UUID menuId) {
        menuRepository.findById(menuId).ifPresent(menu -> {
            menu.setSpecial(false);
            menu.setAvailableFromDate(null);
            menu.setAvailableUntilDate(null);
            // Hidden in POS, but never the last menu shown there (the till would be empty).
            boolean anotherShown = menuRepository.findAllByRestaurantId(menu.getRestaurant().getId()).stream()
                    .anyMatch(other -> !other.getId().equals(menu.getId()) && other.isActive() && other.isShownToWaiters());
            if (anotherShown) {
                menu.setShownToWaiters(false);
            }
            menuRepository.save(menu);
        });
    }
}
