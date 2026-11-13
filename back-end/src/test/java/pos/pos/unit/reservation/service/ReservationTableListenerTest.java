package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pos.pos.order.repository.OrderRepository;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationEventRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.reservation.service.ReservationAvailabilitySupport;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationPolicy;
import pos.pos.reservation.service.ReservationRuleResolver;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.reservation.service.ReservationTableListener;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.repository.SettingsReservationRuleRepository;
import pos.pos.settings.repository.SettingsRepository;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.event.TableStatusChangedEvent;
import pos.pos.tables.repository.RestaurantTableRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Bookings follow the floor plan: seating a checked-in booking's table seats it; clearing the table ends the visit.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Tables and bookings")
class ReservationTableListenerTest {

    @Mock ReservationTableAssignmentRepository assignmentRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock SettingsRepository settingsRepository;
    @Mock SettingsReservationRuleRepository ruleRepository;
    @Mock ReservationAvailabilitySupport availabilitySupport;
    @Mock ReservationEventRepository eventRepository;
    @Mock RestaurantTableRepository tableRepository;
    @Mock OrderRepository orderRepository;

    private ReservationTableListener listener;
    private final Branch branch = new Branch();
    private final RestaurantTable table = new RestaurantTable();

    @BeforeEach
    void setUp() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(UUID.randomUUID());
        restaurant.setTimezone("Europe/Berlin");
        branch.setId(UUID.randomUUID());
        branch.setRestaurant(restaurant);
        table.setId(UUID.randomUUID());
        table.setTableNumber("T4");
        table.setCapacity(4);
        table.setActive(true);
        ReservationPolicy policy = new ReservationPolicy(settingsRepository, new ReservationRuleResolver(ruleRepository));
        ReservationSupport support = new ReservationSupport(null, null, null, null, null, null, null, policy);
        ReservationLifecycleService lifecycle = new ReservationLifecycleService(
                null, support, null, policy, availabilitySupport, eventRepository, tableRepository, orderRepository, event -> { });
        listener = new ReservationTableListener(assignmentRepository, reservationRepository, lifecycle, policy);
        when(tableRepository.findByIdAndBranchIdForUpdate(eq(table.getId()), any())).thenReturn(Optional.of(table));
    }

    private Reservation bookingAtTable(ReservationStatus status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setRestaurant(branch.getRestaurant());
        reservation.setBranch(branch);
        reservation.setStatus(status);
        reservation.setPartySize(3);
        reservation.setReservationStart(now.minusMinutes(10));
        reservation.setReservationEnd(now.plusMinutes(110));
        reservation.setConfirmedAt(now.minusDays(1));
        reservation.setCheckedInAt(now.minusMinutes(5));
        ReservationTableAssignment assignment = new ReservationTableAssignment();
        assignment.setRestaurantTable(table);
        assignment.setPrimaryAssignment(true);
        reservation.addTableAssignment(assignment);
        when(assignmentRepository.findByTableAndReservationStatus(eq(table.getId()), any()))
                .thenAnswer(invocation -> ((java.util.Collection<?>) invocation.getArgument(1)).contains(reservation.getStatus())
                        ? List.of(assignment) : List.of());
        return reservation;
    }

    @Test
    @DisplayName("seating guests at the table of a checked-in booking seats the booking")
    void seatFromFloorPlan() {
        Reservation booking = bookingAtTable(ReservationStatus.CHECKED_IN);
        table.setStatus(TableStatus.OCCUPIED);
        table.setGuestCount(3);
        table.setSeatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        listener.onTableStatusChanged(new TableStatusChangedEvent(null, branch.getId(), table.getId(), TableStatus.AVAILABLE, TableStatus.OCCUPIED, null));

        assertThat(booking.getStatus()).isEqualTo(ReservationStatus.SEATED);
        assertThat(table.getGuestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("clearing the table completes the seated visit")
    void clearingTableCompletes() {
        Reservation booking = bookingAtTable(ReservationStatus.SEATED);
        booking.setSeatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(60));
        table.setStatus(TableStatus.DIRTY);

        listener.onTableStatusChanged(new TableStatusChangedEvent(null, branch.getId(), table.getId(), TableStatus.OCCUPIED, TableStatus.DIRTY, null));

        assertThat(booking.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(booking.getStatusHistory().getLast().getReason()).isEqualTo("Table cleared");
    }
}
