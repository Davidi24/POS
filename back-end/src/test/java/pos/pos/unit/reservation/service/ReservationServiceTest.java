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
import pos.pos.reservation.dto.ReservationAvailabilitySearchRequest;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.UpdateReservationDepositRequest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationDepositStatus;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.mapper.ReservationMapper;
import pos.pos.reservation.repository.ReservationNoteRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationStatusHistoryRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.reservation.service.ReservationAvailabilitySupport;
import pos.pos.reservation.service.ReservationCrudService;
import pos.pos.reservation.service.ReservationDepositService;
import pos.pos.reservation.service.ReservationLifecycleService;
import pos.pos.reservation.service.ReservationQueryService;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.reservation.service.ReservationTableAssignmentService;
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
@DisplayName("Reservation services")
class ReservationServicesTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000803");
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000804");
    private static final UUID TABLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000805");
    private static final UUID RESERVATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000806");
    private static final UUID TABLE_ID_TWO = UUID.fromString("00000000-0000-0000-0000-000000000807");
    private static final UUID TABLE_ID_THREE = UUID.fromString("00000000-0000-0000-0000-000000000808");

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

    private ReservationCrudService reservationCrudService;
    private ReservationLifecycleService reservationLifecycleService;
    private ReservationDepositService reservationDepositService;
    private ReservationQueryService reservationQueryService;

    @BeforeEach
    void setUp() {
        RestaurantTableSupport restaurantTableSupport = new RestaurantTableSupport(
                restaurantTableRepository,
                tableCategoryRepository,
                restaurantTableMapper
        );
        ReservationSupport reservationSupport = new ReservationSupport(
                restaurantScopeService,
                branchRepository,
                customerRepository,
                reservationRepository,
                reservationNoteRepository,
                reservationMapper
        );
        ReservationAvailabilitySupport reservationAvailabilitySupport = new ReservationAvailabilitySupport(
                reservationRepository,
                restaurantTableSupport
        );
        ReservationTableAssignmentService reservationTableAssignmentService = new ReservationTableAssignmentService(
                restaurantScopeService,
                reservationTableAssignmentRepository,
                reservationAvailabilitySupport,
                reservationSupport
        );
        reservationQueryService = new ReservationQueryService(
                restaurantScopeService,
                reservationRepository,
                reservationStatusHistoryRepository,
                reservationTableAssignmentRepository,
                reservationNoteRepository,
                restaurantTableSupport,
                reservationAvailabilitySupport,
                reservationSupport
        );
        reservationCrudService = new ReservationCrudService(
                restaurantScopeService,
                reservationRepository,
                reservationSupport,
                reservationTableAssignmentService
        );
        reservationLifecycleService = new ReservationLifecycleService(
                restaurantScopeService,
                reservationSupport
        );
        reservationDepositService = new ReservationDepositService(
                restaurantScopeService,
                reservationSupport
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

        ReservationResponse response = reservationCrudService.createReservation(authentication, RESTAURANT_ID, ReservationRequest.builder()
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

        ReservationResponse response = reservationLifecycleService.confirmReservation(authentication, RESTAURANT_ID, RESERVATION_ID, null);

        assertThat(response.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.getConfirmedAt()).isNotNull();
        assertThat(reservation.getStatusHistory()).hasSize(1);
        assertThat(reservation.getStatusHistory().get(0).getOldStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(reservation.getStatusHistory().get(0).getNewStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    @DisplayName("Should keep existing deposit amount when deposit update omits amount")
    void shouldKeepExistingDepositAmountWhenDepositUpdateOmitsAmount() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Branch branch = branch(restaurant);
        Reservation reservation = reservation(restaurant, branch);
        reservation.setDepositRequired(true);
        reservation.setDepositAmount(new BigDecimal("25.00"));
        reservation.setDepositStatus(ReservationDepositStatus.PENDING);

        when(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).thenReturn(restaurant);
        when(restaurantScopeService.currentUserId(authentication)).thenReturn(ACTOR_ID);
        when(reservationRepository.findByIdAndRestaurant_Id(RESERVATION_ID, RESTAURANT_ID)).thenReturn(Optional.of(reservation));
        when(reservationRepository.saveAndFlush(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = reservationDepositService.updateDeposit(
                authentication,
                RESTAURANT_ID,
                RESERVATION_ID,
                UpdateReservationDepositRequest.builder().build()
        );

        assertThat(response.getDepositRequired()).isTrue();
        assertThat(response.getDepositAmount()).isEqualByComparingTo("25.00");
        assertThat(response.getDepositStatus()).isEqualTo(ReservationDepositStatus.PENDING);
    }

    @Test
    @DisplayName("Should search availability and prefer a single exact-fit table")
    void shouldSearchAvailabilityAndPreferSingleExactFitTable() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        Branch branch = branch(restaurant);
        RestaurantTable twoTopA = table(branch, TABLE_ID, "A1", 2);
        RestaurantTable fourTop = table(branch, TABLE_ID_TWO, "B1", 4);
        RestaurantTable twoTopB = table(branch, TABLE_ID_THREE, "C1", 2);

        when(restaurantScopeService.requireAccessibleBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(restaurantScopeService.requireExistingBranch(RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(restaurantTableRepository.findAllByBranch_IdOrderByFloorAscNameAsc(BRANCH_ID)).thenReturn(List.of(twoTopA, fourTop, twoTopB));
        when(reservationRepository.findAllByBranch_IdAndStatusInAndReservationStartLessThanAndReservationEndGreaterThanOrderByReservationStartAsc(
                eq(BRANCH_ID),
                any(),
                any(),
                any()
        )).thenReturn(List.of());

        var response = reservationQueryService.searchAvailability(
                authentication,
                RESTAURANT_ID,
                BRANCH_ID,
                ReservationAvailabilitySearchRequest.builder()
                        .reservationStart(OffsetDateTime.parse("2026-05-10T18:00:00Z"))
                        .reservationEnd(OffsetDateTime.parse("2026-05-10T20:00:00Z"))
                        .partySize(4)
                        .maxOptions(2)
                        .build()
        );

        assertThat(response).hasSize(2);
        assertThat(response.get(0).getTableIds()).containsExactly(TABLE_ID_TWO);
        assertThat(response.get(0).getExactFit()).isTrue();
        assertThat(response.get(1).getTableIds()).containsExactly(TABLE_ID, TABLE_ID_THREE);
        assertThat(response.get(1).getTableCount()).isEqualTo(2);
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
        return table(branch, TABLE_ID, "A1", 4);
    }

    private RestaurantTable table(Branch branch, UUID tableId, String tableNumber, int capacity) {
        RestaurantTable table = new RestaurantTable();
        table.setId(tableId);
        table.setRestaurant(branch.getRestaurant());
        table.setBranch(branch);
        table.setTableNumber(tableNumber);
        table.setName(tableNumber);
        table.setCapacity(capacity);
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
