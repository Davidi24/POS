package pos.pos.unit.reservation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import pos.pos.common.dto.PageResponse;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.ReservationSummaryResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationNoteRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationStatusHistoryRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.reservation.service.ReservationAvailabilitySupport;
import pos.pos.reservation.service.ReservationQueryService;
import pos.pos.reservation.service.ReservationRuleResolver;
import pos.pos.reservation.service.ReservationSupport;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.service.RestaurantTableSupport;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The overview's Arriving feed (paged, optionally one floor) and its floor-aware day summary.
@ExtendWith(MockitoExtension.class)
class ReservationArrivalsAndSummaryTest {

    private static final UUID RESTAURANT_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Mock RestaurantScopeService restaurantScopeService;
    @Mock ReservationRepository reservationRepository;
    @Mock ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    @Mock ReservationTableAssignmentRepository reservationTableAssignmentRepository;
    @Mock ReservationNoteRepository reservationNoteRepository;
    @Mock RestaurantTableSupport restaurantTableSupport;
    @Mock ReservationAvailabilitySupport reservationAvailabilitySupport;
    @Mock ReservationSupport reservationSupport;
    @Mock ReservationRuleResolver reservationRuleResolver;
    @Mock pos.pos.reservation.service.ReservationPolicy reservationPolicy;
    @Mock Authentication authentication;
    @InjectMocks ReservationQueryService reservationQueryService;

    @org.junit.jupiter.api.BeforeEach
    void defaults() {
        org.mockito.Mockito.lenient().when(reservationPolicy.values(any())).thenReturn(new pos.pos.reservation.service.ReservationPolicy.Values(
                5, 15, 7, 30, 20, 15, 120, java.time.LocalTime.of(15, 0), 120, 120, 24, 60, 15, 30, 1, 7));
    }

    @SuppressWarnings("unchecked")
    @Test
    void arrivalsPageThroughPendingAndConfirmedGuestsInOrder() {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC);
        Reservation first = reservation(ReservationStatus.CONFIRMED, from.plusMinutes(10), 2);
        Reservation second = reservation(ReservationStatus.PENDING, from.plusMinutes(40), 4);
        when(restaurantScopeService.requireAccessibleBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(new Branch());
        when(reservationRepository.findUpcomingIdPage(eq(BRANCH_ID), any(), eq(from), any()))
                .thenReturn(new PageImpl<>(List.of(first.getId(), second.getId()), PageRequest.of(1, 2), 7));
        // The rows come back in any order; the page keeps the order of its ids.
        when(reservationRepository.findAllByIdInOrderByReservationStartAsc(anyList())).thenReturn(List.of(second, first));
        when(reservationSupport.toResponses(anyList())).thenAnswer(call -> ((List<Reservation>) call.getArgument(0)).stream()
                .map(reservation -> response(reservation)).toList());

        PageResponse<ReservationResponse> page = reservationQueryService.getArrivals(authentication, RESTAURANT_ID, BRANCH_ID, from, null, 1, 2);

        assertThat(page.getItems()).extracting(ReservationResponse::getId).containsExactly(first.getId(), second.getId());
        assertThat(page.isHasNext()).isTrue();
        ArgumentCaptor<Collection<ReservationStatus>> statuses = ArgumentCaptor.captor();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.captor();
        verify(reservationRepository).findUpcomingIdPage(eq(BRANCH_ID), statuses.capture(), eq(from), pageable.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
    }

    @Test
    void arrivalsForOneFloorUseTheFloorQueryAndCapThePageSize() {
        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC);
        when(restaurantScopeService.requireAccessibleBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(new Branch());
        when(reservationRepository.findUpcomingIdPageOnFloor(eq(BRANCH_ID), any(), eq(from), eq("1st Floor"), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        PageResponse<ReservationResponse> page = reservationQueryService.getArrivals(authentication, RESTAURANT_ID, BRANCH_ID, from, "  1st Floor ", 0, 500);

        assertThat(page.getItems()).isEmpty();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.captor();
        verify(reservationRepository).findUpcomingIdPageOnFloor(eq(BRANCH_ID), any(), eq(from), eq("1st Floor"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        verify(reservationRepository, never()).findUpcomingIdPage(any(), any(), any(), any());
    }

    @Test
    void summaryForOneFloorCountsItsBookingsAndThoseWithoutATable() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Reservation soonOnFloor = reservation(ReservationStatus.PENDING, now.plusMinutes(30), 2, "1st Floor");
        Reservation laterNoTable = reservation(ReservationStatus.CONFIRMED, now.plusHours(5), 4);
        Reservation seatedElsewhere = reservation(ReservationStatus.SEATED, now.minusMinutes(20), 6, "Terrace");
        Reservation seatedNoTable = reservation(ReservationStatus.SEATED, now.minusMinutes(50), 3);
        Reservation cancelledNoTable = reservation(ReservationStatus.CANCELLED, now.plusHours(1), 5);
        Branch branch = new Branch();
        when(restaurantScopeService.requireAccessibleBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(reservationSupport.resolveSummaryWindow(branch, null, null))
                .thenReturn(new ReservationSupport.TimeWindow(now.minusHours(12), now.plusHours(12)));
        when(reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(eq(BRANCH_ID), any(), any()))
                .thenReturn(List.of(seatedNoTable, seatedElsewhere, soonOnFloor, cancelledNoTable, laterNoTable));

        ReservationSummaryResponse summary = reservationQueryService.getReservationSummary(authentication, RESTAURANT_ID, BRANCH_ID, null, null, "1st floor");

        // Terrace's seated party is left out; everything without a table counts on every floor.
        assertThat(summary.getTotalReservations()).isEqualTo(4);
        assertThat(summary.getPresentGuests()).isEqualTo(2 + 4 + 3);
        assertThat(summary.getGuestsToArrive()).isEqualTo(2 + 4);
        assertThat(summary.getUnassignedCount()).isEqualTo(2);
        assertThat(summary.getArrivingSoonCount()).isEqualTo(1);
        assertThat(summary.getArrivingSoonGuests()).isEqualTo(2);
    }

    @Test
    void summaryWithoutAFloorCountsTheWholeBranch() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Branch branch = new Branch();
        when(restaurantScopeService.requireAccessibleBranch(authentication, RESTAURANT_ID, BRANCH_ID)).thenReturn(branch);
        when(reservationSupport.resolveSummaryWindow(branch, null, null))
                .thenReturn(new ReservationSupport.TimeWindow(now.minusHours(12), now.plusHours(12)));
        when(reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(eq(BRANCH_ID), any(), any()))
                .thenReturn(List.of(
                        reservation(ReservationStatus.CONFIRMED, now.plusMinutes(5), 2, "1st Floor"),
                        reservation(ReservationStatus.CONFIRMED, now.plusMinutes(90), 3, "Terrace"),
                        reservation(ReservationStatus.NO_SHOW, now.minusHours(1), 8)
                ));

        ReservationSummaryResponse summary = reservationQueryService.getReservationSummary(authentication, RESTAURANT_ID, BRANCH_ID, null, null, null);

        assertThat(summary.getTotalReservations()).isEqualTo(3);
        assertThat(summary.getArrivingSoonCount()).isEqualTo(2);
        assertThat(summary.getArrivingSoonGuests()).isEqualTo(5);
        // A no-show isn't waiting for a table.
        assertThat(summary.getUnassignedCount()).isZero();
    }

    private static Reservation reservation(ReservationStatus status, OffsetDateTime start, int partySize, String... tableFloors) {
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID());
        reservation.setStatus(status);
        reservation.setReservationStart(start);
        reservation.setPartySize(partySize);
        for (String floor : tableFloors) {
            RestaurantTable table = new RestaurantTable();
            table.setFloor(floor);
            ReservationTableAssignment assignment = new ReservationTableAssignment();
            assignment.setReservation(reservation);
            assignment.setRestaurantTable(table);
            reservation.getTableAssignments().add(assignment);
        }
        return reservation;
    }

    private static ReservationResponse response(Reservation reservation) {
        return ReservationResponse.builder().id(reservation.getId()).build();
    }
}
