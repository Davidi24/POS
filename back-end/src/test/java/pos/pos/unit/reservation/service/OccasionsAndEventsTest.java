package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.repository.MenuRepository;
import pos.pos.reservation.dto.ReservationOccasionDto;
import pos.pos.reservation.dto.RestaurantEventDto;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationOccasion;
import pos.pos.reservation.entity.RestaurantEvent;
import pos.pos.reservation.repository.ReservationOccasionRepository;
import pos.pos.reservation.repository.RestaurantEventRepository;
import pos.pos.reservation.service.ReservationOccasionService;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.reservation.service.RestaurantEventService;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Phase 3: occasions on bookings (defaults, options) and the restaurant's event nights with their special menu.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Occasions and event nights")
class OccasionsAndEventsTest {

    @Mock RestaurantScopeService scope;
    @Mock ReservationOccasionRepository occasionRepository;
    @Mock RestaurantEventRepository eventRepository;
    @Mock MenuRepository menuRepository;
    @Mock ReservationSupport support;
    @Mock Authentication authentication;

    private final Restaurant restaurant = new Restaurant();
    private final List<ReservationOccasion> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        restaurant.setId(UUID.randomUUID());
        restaurant.setTimezone("Europe/Berlin");
        when(scope.requireAccessibleRestaurant(authentication, restaurant.getId())).thenReturn(restaurant);
        when(scope.requireManageableRestaurant(authentication, restaurant.getId())).thenReturn(restaurant);
        when(occasionRepository.findAllByRestaurantIdOrderByDisplayOrderAscNameAsc(restaurant.getId())).thenAnswer(call -> List.copyOf(stored));
        when(occasionRepository.save(any(ReservationOccasion.class))).thenAnswer(call -> {
            ReservationOccasion occasion = call.getArgument(0);
            // Like the database: a new occasion gets its id when first saved.
            if (occasion.getId() == null) occasion.setId(UUID.randomUUID());
            if (!stored.contains(occasion)) stored.add(occasion);
            return occasion;
        });
        when(support.restaurantZone(any())).thenReturn(java.time.ZoneId.of("Europe/Berlin"));
    }

    private ReservationOccasionService occasions() {
        return new ReservationOccasionService(scope, occasionRepository);
    }

    @Test
    @DisplayName("the agreed occasions are there the first time, each with its icon and options")
    void defaults() {
        List<ReservationOccasionDto> list = occasions().getOccasions(authentication, restaurant.getId());

        assertThat(list).extracting(ReservationOccasionDto::getName)
                .containsExactly("Birthday", "Anniversary", "Engagement", "Graduation", "Date night", "Business", "Other");
        assertThat(list.getFirst().getIcon()).isEqualTo("🎂");
        assertThat(list.getFirst().getOptions()).contains("Cake from us", "Candles", "Birthday song");
    }

    @Test
    @DisplayName("a booking keeps the picked occasion, its options and a note")
    void applyToBooking() {
        Reservation booking = new Reservation();
        booking.setRestaurant(restaurant);

        occasions().apply(booking, "birthday", List.of("Candles", "Cake from us"), "30 candles at dessert");

        assertThat(booking.getOccasionCode()).isEqualTo("BIRTHDAY");
        assertThat(booking.getOccasionName()).isEqualTo("Birthday");
        assertThat(booking.getOccasionIcon()).isEqualTo("🎂");
        assertThat(booking.getOccasionOptions()).isEqualTo("Candles\nCake from us");
        assertThat(booking.getOccasionNote()).isEqualTo("30 candles at dessert");
        assertThatThrownBy(() -> occasions().apply(booking, "BIRTHDAY", List.of("Fireworks"), null)).hasMessageContaining("Pick options");
        assertThatThrownBy(() -> occasions().apply(booking, "WEDDING", List.of(), null)).hasMessageContaining("isn't available");

        occasions().apply(booking, "", null, null);
        assertThat(booking.getOccasionCode()).isNull();
    }

    @Test
    @DisplayName("saving the list renames, adds and removes occasions")
    void saveList() {
        occasions().getOccasions(authentication, restaurant.getId());

        List<ReservationOccasionDto> saved = occasions().saveOccasions(authentication, restaurant.getId(), List.of(
                ReservationOccasionDto.builder().code("BIRTHDAY").name("Compleanno").icon("🎂").options(List.of("Torta")).build(),
                ReservationOccasionDto.builder().name("Baby shower").icon("🍼").options(List.of()).build()
        ));

        assertThat(saved).extracting(ReservationOccasionDto::getCode).containsExactly("BIRTHDAY", "BABY_SHOWER");
        assertThat(saved.getFirst().getName()).isEqualTo("Compleanno");
        verify(occasionRepository, org.mockito.Mockito.times(6)).delete(any(ReservationOccasion.class));
    }

    // ---- Events ----

    private RestaurantEventService events() {
        return new RestaurantEventService(scope, eventRepository, menuRepository, support);
    }

    @Test
    @DisplayName("an event's menu becomes a special menu shown only on its days")
    void eventMenu() {
        Menu menu = new Menu();
        menu.setId(UUID.randomUUID());
        menu.setName("Valentine's menu");
        menu.setRestaurant(restaurant);
        when(menuRepository.findByIdAndRestaurantDeletedAtIsNull(menu.getId())).thenReturn(Optional.of(menu));
        when(menuRepository.findById(menu.getId())).thenReturn(Optional.of(menu));
        when(eventRepository.saveAndFlush(any(RestaurantEvent.class))).thenAnswer(call -> call.getArgument(0));

        RestaurantEventDto saved = events().createEvent(authentication, restaurant.getId(), RestaurantEventDto.builder()
                .name("Valentine's").icon("❤️").startDate(LocalDate.of(2027, 2, 14)).endDate(LocalDate.of(2027, 2, 14))
                .menuId(menu.getId()).specialMenuOnly(true).build());

        assertThat(saved.getMenuName()).isEqualTo("Valentine's menu");
        assertThat(saved.getSpecialMenuOnly()).isTrue();
        assertThat(menu.isSpecial()).isTrue();
        assertThat(menu.getAvailableFromDate()).isEqualTo(LocalDate.of(2027, 2, 14));
        assertThat(menu.getAvailableUntilDate()).isEqualTo(LocalDate.of(2027, 2, 14));
    }

    @Test
    @DisplayName("two events can't share a day, and an event can't end before it starts")
    void eventDates() {
        RestaurantEvent newYear = new RestaurantEvent();
        newYear.setId(UUID.randomUUID());
        when(eventRepository.findActiveBetween(eq(restaurant.getId()), any(), any())).thenReturn(List.of(newYear));

        assertThatThrownBy(() -> events().createEvent(authentication, restaurant.getId(), RestaurantEventDto.builder()
                .name("Party").icon("🎉").startDate(LocalDate.of(2026, 12, 31)).endDate(LocalDate.of(2026, 12, 31)).build()))
                .hasMessageContaining("Another event");
        assertThatThrownBy(() -> events().createEvent(authentication, restaurant.getId(), RestaurantEventDto.builder()
                .name("Party").icon("🎉").startDate(LocalDate.of(2026, 12, 31)).endDate(LocalDate.of(2026, 12, 30)).build()))
                .hasMessageContaining("end on or after");
    }
}
