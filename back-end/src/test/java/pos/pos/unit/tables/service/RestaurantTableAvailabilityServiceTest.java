package pos.pos.unit.tables.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.tables.dto.TableAvailabilityResponse;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.mapper.RestaurantTableMapper;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.repository.TableCategoryRepository;
import pos.pos.tables.service.RestaurantTableAvailabilityService;
import pos.pos.tables.service.RestaurantTableSupport;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantTableAvailabilityService")
class RestaurantTableAvailabilityServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000731");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000732");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000733");
    private static final UUID TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000734");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000735");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RestaurantTableRepository restaurantTableRepository;

    @Mock
    private TableCategoryRepository tableCategoryRepository;

    @Spy
    private RestaurantTableMapper restaurantTableMapper;

    private RestaurantTableAvailabilityService restaurantTableAvailabilityService;

    @BeforeEach
    void setUp() {
        RestaurantTableSupport restaurantTableSupport = new RestaurantTableSupport(
                restaurantTableRepository,
                tableCategoryRepository,
                restaurantTableMapper
        );
        restaurantTableAvailabilityService = new RestaurantTableAvailabilityService(
                restaurantScopeService,
                reservationRepository,
                restaurantTableSupport
        );
    }

    @Test
    @DisplayName("Should mark table unavailable when reservation overlaps requested window")
    void shouldMarkTableUnavailableWhenReservationOverlapsRequestedWindow() {
        Authentication authentication = authentication();
        Branch branch = branch();
        RestaurantTable table = table(branch, TABLE_ID, "A1", 4);

        Reservation reservation = new Reservation();
        reservation.setId(RESERVATION_ID);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        ReservationTableAssignment assignment = new ReservationTableAssignment();
        assignment.setRestaurantTable(table);
        reservation.addTableAssignment(assignment);

        OffsetDateTime from = OffsetDateTime.parse("2026-05-07T18:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-05-07T19:30:00Z");

        when(restaurantScopeService.requireAccessibleBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(restaurantTableRepository.findAllByBranch_IdOrderByFloorAscNameAsc(BRANCH_ID)).thenReturn(List.of(table));
        when(reservationRepository.findAllByBranch_IdAndStatusInAndReservationStartLessThanAndReservationEndGreaterThanOrderByReservationStartAsc(
                eq(BRANCH_ID), anyCollection(), eq(to), eq(from)
        )).thenReturn(List.of(reservation));

        List<TableAvailabilityResponse> response = restaurantTableAvailabilityService.getTableAvailability(
                authentication,
                RESTAURANT_ID,
                BRANCH_ID,
                from,
                to,
                2
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getAvailableForRequestedWindow()).isFalse();
        assertThat(response.getFirst().getBlockingReason()).isEqualTo("RESERVED_FOR_REQUESTED_WINDOW");
        assertThat(response.getFirst().getOverlappingReservationIds()).containsExactly(RESERVATION_ID);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("tables.admin@pos.example")
                        .username("tables.admin")
                        .active(true)
                        .build(),
                null
        );
    }

    private Branch branch() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);

        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        return branch;
    }

    private RestaurantTable table(Branch branch, UUID id, String tableNumber, int capacity) {
        RestaurantTable table = new RestaurantTable();
        table.setId(id);
        table.setRestaurant(branch.getRestaurant());
        table.setBranch(branch);
        table.setTableNumber(tableNumber);
        table.setName(tableNumber);
        table.setCapacity(capacity);
        table.setShape(TableShape.RECTANGLE);
        table.setStatus(TableStatus.AVAILABLE);
        table.setActive(true);
        table.setCreatedAt(OffsetDateTime.parse("2026-05-06T10:00:00Z"));
        table.setUpdatedAt(OffsetDateTime.parse("2026-05-06T10:00:00Z"));
        return table;
    }
}
