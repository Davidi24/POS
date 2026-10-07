package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.reservation.dto.RestaurantEventDto;
import pos.pos.reservation.entity.RestaurantEvent;
import pos.pos.reservation.repository.RestaurantEventRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// The restaurant's own nights (Admin Hub → Settings → Reservations → Event nights): a name, days, an icon and the
// special menu that shows only then. Bookings those days stay normal and show the event icon. Choosing a menu makes
// it a special menu available only on the event's days.
@Service
@RequiredArgsConstructor
public class RestaurantEventService {

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantEventRepository restaurantEventRepository;
    private final MenuRepository menuRepository;
    private final ReservationSupport reservationSupport;

    // Events still to come or on now (and the last month's, to look back).
    @Transactional(readOnly = true)
    public List<RestaurantEventDto> getEvents(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        LocalDate today = LocalDate.now(reservationSupport.restaurantZone(restaurant));
        return restaurantEventRepository.findAllByRestaurantIdAndEndDateGreaterThanEqualOrderByStartDateAsc(restaurantId, today.minusMonths(1))
                .stream().map(this::toDto).toList();
    }

    // The event on one day, if any (for that day's bookings header and the POS menus).
    @Transactional(readOnly = true)
    public RestaurantEventDto getEventOn(Authentication authentication, UUID restaurantId, LocalDate date) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return restaurantEventRepository.findActiveBetween(restaurantId, date, date).stream().findFirst().map(this::toDto).orElse(null);
    }

    @Transactional
    public RestaurantEventDto createEvent(Authentication authentication, UUID restaurantId, RestaurantEventDto request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        RestaurantEvent event = new RestaurantEvent();
        event.setRestaurantId(restaurantId);
        event.setCreatedBy(restaurantScopeService.currentUserId(authentication));
        return save(authentication, restaurantId, event, request);
    }

    @Transactional
    public RestaurantEventDto updateEvent(Authentication authentication, UUID restaurantId, UUID eventId, RestaurantEventDto request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        RestaurantEvent event = restaurantEventRepository.findByIdAndRestaurantId(eventId, restaurantId)
                .orElseThrow(() -> new AuthException("Event not found", HttpStatus.NOT_FOUND));
        return save(authentication, restaurantId, event, request);
    }

    @Transactional
    public void deleteEvent(Authentication authentication, UUID restaurantId, UUID eventId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        RestaurantEvent event = restaurantEventRepository.findByIdAndRestaurantId(eventId, restaurantId)
                .orElseThrow(() -> new AuthException("Event not found", HttpStatus.NOT_FOUND));
        restaurantEventRepository.delete(event);
    }

    private RestaurantEventDto save(Authentication authentication, UUID restaurantId, RestaurantEvent event, RestaurantEventDto request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new AuthException("The event must end on or after its first day", HttpStatus.BAD_REQUEST);
        }
        boolean overlaps = restaurantEventRepository.findActiveBetween(restaurantId, request.getStartDate(), request.getEndDate()).stream()
                .anyMatch(other -> !Objects.equals(other.getId(), event.getId()));
        if (overlaps && !Boolean.FALSE.equals(request.getActive())) {
            throw new AuthException("Another event is on those days already", HttpStatus.CONFLICT);
        }
        event.setName(request.getName().trim());
        event.setIcon(request.getIcon().trim());
        event.setStartDate(request.getStartDate());
        event.setEndDate(request.getEndDate());
        event.setSpecialMenuOnly(Boolean.TRUE.equals(request.getSpecialMenuOnly()));
        event.setActive(request.getActive() == null || request.getActive());
        event.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        event.setMenuId(null);
        if (request.getMenuId() != null) {
            Menu menu = menuRepository.findByIdAndRestaurantDeletedAtIsNull(request.getMenuId())
                    .filter(candidate -> Objects.equals(candidate.getRestaurant().getId(), restaurantId))
                    .orElseThrow(() -> new AuthException("Choose one of this restaurant's menus", HttpStatus.BAD_REQUEST));
            // The event's menu is a special menu shown only on its days.
            menu.setSpecial(true);
            menu.setAvailableFromDate(request.getStartDate());
            menu.setAvailableUntilDate(request.getEndDate());
            menuRepository.save(menu);
            event.setMenuId(menu.getId());
        }
        return toDto(restaurantEventRepository.saveAndFlush(event));
    }

    private RestaurantEventDto toDto(RestaurantEvent event) {
        String menuName = event.getMenuId() == null ? null : menuRepository.findById(event.getMenuId()).map(Menu::getName).orElse(null);
        return RestaurantEventDto.builder()
                .id(event.getId())
                .name(event.getName())
                .icon(event.getIcon())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .menuId(event.getMenuId())
                .menuName(menuName)
                .specialMenuOnly(event.isSpecialMenuOnly())
                .active(event.isActive())
                .build();
    }
}
