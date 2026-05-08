package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.customer.entity.Customer;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationDepositStatus;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.mapper.ReservationMapper;
import pos.pos.reservation.repository.ReservationNoteRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationStatusHistoryRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.reservation.service.ReservationService;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.mapper.RestaurantTableMapper;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.repository.TableCategoryRepository;
import pos.pos.tables.service.RestaurantTableSupport;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReservationService")
class ReservationServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000803");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000804");
    private static final UUID TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000805");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000806");

    @Mock
    private RestaurantScopeService restaurantScopeService;
    @Mock
    private BranchRepository branchRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    @Mock
    private ReservationTableAssignmentRepository reservationTableAssignmentRepository;
    @Mock
    private ReservationNoteRepository reservationNoteRepository;
    @Mock
    private RestaurantTableRepository restaurantTableRepository;
    @Mock
    private TableCategoryRepository tableCategoryRepository;
    @Spy
    private RestaurantTableMapper restaurantTableMapper;
    @Spy
    private ReservationMapper reservationMapper;

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        RestaurantTableSupport restaurantTableSupport = new RestaurantTableSupport(
                restaurantTableRepository,
                tableCategoryRepository,
                restaurantTableMapper
        );

        reservationService = new ReservationService(
                restaurantScopeService,
                branchRepository,
                customerRepository,
                reservationRepository,
                reservationStatusHistoryRepository,
                reservationTableAssignmentRepository,
                reservationNoteRepository,
                restaurantTableRepository,
                restaurantTableSupport,
                reservationMapper
        );
    }

    @Test
    @DisplayName("Should create reservation with generated code, deposit pending, and initial table assignment")
    void shouldCreateReservationWithGeneratedCodeDepositAndInitialTableAssignment() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Branch branch = branch(restaurant);
        Customer customer = customer(restaurant);
        RestaurantTable table = table(branch);

        when(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(restaurantScopeService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(customerRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(CUSTOMER_ID, RESTAURANT_ID)).thenReturn(Optional.of(customer));
        when(reservationRepository.existsByRestaurant_IdAndReservationCode(eq(RESTAURANT_ID), any())).thenReturn(false);
        when(restaurantTableRepository.findAllByBranch_IdOrderByFloorAscNameAsc(BRANCH_ID)).thenReturn(List.of(table));
        when(reservationRepository.findAllByBranch_IdAndStatusInAndReservationStartLessThanAndReservationEndGreaterThanOrderByReservationStartAsc(
                eq(BRANCH_ID),
                any(),
                any(),
                any()
        )).thenReturn(List.of());
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation reservation = invocation.getArgument(0);
            reservation.setId(RESERVATION_ID);
            reservation.setCreatedAt(OffsetDateTime.parse("2026-05-07T12:00:00Z"));
            reservation.setUpdatedAt(OffsetDateTime.parse("2026-05-07T12:00:00Z"));
            return reservation;
        });

        ReservationResponse response = reservationService.createReservation(authentication, RESTAURANT_ID, ReservationRequest.builder()
                .branchId(BRANCH_ID)
                .customerId(CUSTOMER_ID)
                .partySize(4)
                .reservationStart(OffsetDateTime.parse("2026-05-10T18:00:00Z"))
                .reservationEnd(OffsetDateTime.parse("2026-05-10T20:00:00Z"))
                .depositRequired(true)
                .depositAmount(new BigDecimal("25.00"))
                .initialTableIds(List.of(TABLE_ID))
                .primaryTableId(TABLE_ID)
                .build());

        assertThat(response.getId()).isEqualTo(RESERVATION_ID);
        assertThat(response.getReservationCode()).startsWith("RES_");
        assertThat(response.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.getDepositStatus()).isEqualTo(ReservationDepositStatus.PENDING);
        assertThat(response.getTableAssignments()).hasSize(1);
        assertThat(response.getTableAssignments().get(0).getTableId()).isEqualTo(TABLE_ID);
        assertThat(response.getTableAssignments().get(0).getPrimary()).isTrue();
    }

    @Test
    @DisplayName("Should confirm reservation and stamp confirmedAt")
    void shouldConfirmReservationAndStampConfirmedAt() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Branch branch = branch(restaurant);
        Reservation reservation = reservation(restaurant, branch);

        when(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(restaurantScopeService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(reservationRepository.findByIdAndRestaurant_Id(RESERVATION_ID, RESTAURANT_ID)).thenReturn(Optional.of(reservation));
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.confirmReservation(authentication, RESTAURANT_ID, RESERVATION_ID, null);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.getConfirmedAt()).isNotNull();
        assertThat(reservation.getStatusHistory()).hasSize(1);
        assertThat(reservation.getStatusHistory().get(0).getOldStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getStatusHistory().get(0).getNewStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("reservation.admin@pos.example")
                        .username("reservation.admin")
                        .active(true)
                        .build(),
                null
        );
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("Reservation Restaurant");
        restaurant.setLegalName("Reservation Restaurant LLC");
        restaurant.setCode("RESERVATION_RESTAURANT");
        restaurant.setSlug("reservation-restaurant");
        restaurant.setCurrency("USD");
        restaurant.setTimezone("Europe/Berlin");
        restaurant.setActive(true);
        return restaurant;
    }

    private Branch branch(Restaurant restaurant) {
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        branch.setName("Main Branch");
        branch.setCode("MAIN");
        branch.setActive(true);
        return branch;
    }

    private Customer customer(Restaurant restaurant) {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        customer.setRestaurant(restaurant);
        customer.setFirstName("Alex");
        customer.setLastName("Stone");
        customer.setEmail("alex.stone@pos.example");
        customer.setPhone("+49123456789");
        customer.setActive(true);
        return customer;
    }

    private RestaurantTable table(Branch branch) {
        RestaurantTable table = new RestaurantTable();
        table.setId(TABLE_ID);
        table.setRestaurant(branch.getRestaurant());
        table.setBranch(branch);
        table.setTableNumber("A1");
        table.setName("A1");
        table.setCapacity(4);
        table.setStatus(pos.pos.tables.enums.TableStatus.AVAILABLE);
        table.setActive(true);
        table.setCreatedAt(OffsetDateTime.parse("2026-05-06T10:00:00Z"));
        table.setUpdatedAt(OffsetDateTime.parse("2026-05-06T10:00:00Z"));
        return table;
    }

    private Reservation reservation(Restaurant restaurant, Branch branch) {
        Reservation reservation = new Reservation();
        reservation.setId(RESERVATION_ID);
        reservation.setRestaurant(restaurant);
        reservation.setBranch(branch);
        reservation.setReservationCode("RES_TEST");
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPartySize(2);
        reservation.setReservationStart(OffsetDateTime.parse("2026-05-10T18:00:00Z"));
        reservation.setReservationEnd(OffsetDateTime.parse("2026-05-10T19:30:00Z"));
        reservation.setContactName("Alex Stone");
        reservation.setCreatedAt(OffsetDateTime.parse("2026-05-07T11:00:00Z"));
        reservation.setUpdatedAt(OffsetDateTime.parse("2026-05-07T11:00:00Z"));
        return reservation;
    }
}
