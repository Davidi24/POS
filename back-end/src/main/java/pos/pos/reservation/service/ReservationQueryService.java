package pos.pos.reservation.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.ReservationAuditResponse;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.dto.ReservationAvailabilitySearchRequest;
import pos.pos.reservation.dto.ReservationCapacityResponse;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.ReservationStatusHistoryResponse;
import pos.pos.reservation.dto.ReservationSummaryResponse;
import pos.pos.reservation.dto.ReservationTableAssignmentResponse;
import pos.pos.reservation.dto.ReservationTimelineEventResponse;
import pos.pos.reservation.dto.ReservationValidationRequest;
import pos.pos.reservation.dto.ReservationValidationResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.repository.ReservationNoteRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationStatusHistoryRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.service.RestaurantTableSupport;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@lombok.RequiredArgsConstructor
public class ReservationQueryService {

    private static final EnumSet<ReservationStatus> UPCOMING_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.SEATED
    );
    private static final int DEFAULT_AVAILABILITY_LIMIT = 10;
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 3;

    private final RestaurantScopeService restaurantScopeService;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    private final ReservationTableAssignmentRepository reservationTableAssignmentRepository;
    private final ReservationNoteRepository reservationNoteRepository;
    private final RestaurantTableSupport restaurantTableSupport;
    private final ReservationAvailabilitySupport reservationAvailabilitySupport;
    private final ReservationSupport reservationSupport;

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return reservationRepository.findAllByRestaurant_IdOrderByReservationStartDesc(restaurantId).stream()
                .map(reservationSupport::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return reservationSupport.toResponse(reservationSupport.requireReservation(restaurantId, reservationId));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getBranchReservations(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            ReservationStatus status,
            UUID customerId
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        List<Reservation> reservations = loadBranchReservations(branchId, from, to);

        return reservations.stream()
                .filter(reservation -> status == null || reservation.getStatus() == status)
                .filter(reservation -> customerId == null || Objects.equals(
                        reservation.getCustomer() == null ? null : reservation.getCustomer().getId(),
                        customerId
                ))
                .map(reservationSupport::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getBranchReservationCalendar(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        OffsetDateTime resolvedFrom = from;
        OffsetDateTime resolvedTo = to;
        if (resolvedFrom == null && resolvedTo == null) {
            LocalDate currentDate = LocalDate.now(reservationSupport.restaurantZone(branch.getRestaurant()));
            resolvedFrom = currentDate.withDayOfMonth(1)
                    .atStartOfDay(reservationSupport.restaurantZone(branch.getRestaurant()))
                    .toOffsetDateTime();
            resolvedTo = currentDate.withDayOfMonth(1)
                    .plusMonths(1)
                    .atStartOfDay(reservationSupport.restaurantZone(branch.getRestaurant()))
                    .toOffsetDateTime();
        } else {
            reservationSupport.requireCompleteWindow(resolvedFrom, resolvedTo);
        }

        return reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(
                        branchId,
                        resolvedFrom,
                        resolvedTo
                ).stream()
                .map(reservationSupport::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getTodayReservations(Authentication authentication, UUID restaurantId, UUID branchId) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        ReservationSupport.TimeWindow window = reservationSupport.dayWindow(
                branch,
                LocalDate.now(reservationSupport.restaurantZone(branch.getRestaurant()))
        );
        return reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(
                        branchId,
                        window.from(),
                        window.to()
                ).stream()
                .map(reservationSupport::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getUpcomingReservations(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            Integer limit
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        int resolvedLimit = limit == null || limit <= 0 ? 20 : limit;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return reservationRepository.findAllByBranch_IdOrderByReservationStartAsc(branchId).stream()
                .filter(reservation -> !reservation.getReservationStart().isBefore(now))
                .filter(reservation -> UPCOMING_STATUSES.contains(reservation.getStatus()))
                .limit(resolvedLimit)
                .map(reservationSupport::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationAvailabilityOptionResponse> searchAvailability(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            ReservationAvailabilitySearchRequest request
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return reservationAvailabilitySupport.availabilityOptionsForBranch(
                restaurantScopeService.requireExistingBranch(restaurantId, branchId),
                request.getReservationStart(),
                request.getReservationEnd(),
                request.getPartySize(),
                reservationSupport.resolveAvailabilityLimit(request.getMaxOptions(), DEFAULT_AVAILABILITY_LIMIT)
        );
    }

    @Transactional(readOnly = true)
    public List<ReservationAvailabilityOptionResponse> recommendAvailability(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            ReservationAvailabilitySearchRequest request
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return reservationAvailabilitySupport.availabilityOptionsForBranch(
                restaurantScopeService.requireExistingBranch(restaurantId, branchId),
                request.getReservationStart(),
                request.getReservationEnd(),
                request.getPartySize(),
                reservationSupport.resolveAvailabilityLimit(request.getMaxOptions(), DEFAULT_RECOMMENDATION_LIMIT)
        );
    }

    @Transactional(readOnly = true)
    public ReservationSummaryResponse getReservationSummary(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        ReservationSupport.TimeWindow window = reservationSupport.resolveSummaryWindow(branch, from, to);
        List<Reservation> reservations = reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(
                branchId,
                window.from(),
                window.to()
        );

        return ReservationSummaryResponse.builder()
                .branchId(branchId)
                .from(window.from())
                .to(window.to())
                .totalReservations(reservations.size())
                .totalGuests(reservations.stream().mapToInt(Reservation::getPartySize).sum())
                .pendingCount(reservationSupport.countByStatus(reservations, ReservationStatus.PENDING))
                .confirmedCount(reservationSupport.countByStatus(reservations, ReservationStatus.CONFIRMED))
                .checkedInCount(reservationSupport.countByStatus(reservations, ReservationStatus.CHECKED_IN))
                .seatedCount(reservationSupport.countByStatus(reservations, ReservationStatus.SEATED))
                .completedCount(reservationSupport.countByStatus(reservations, ReservationStatus.COMPLETED))
                .cancelledCount(reservationSupport.countByStatus(reservations, ReservationStatus.CANCELLED))
                .noShowCount(reservationSupport.countByStatus(reservations, ReservationStatus.NO_SHOW))
                .upcomingCount((int) reservationRepository.findAllByBranch_IdOrderByReservationStartAsc(branchId).stream()
                        .filter(reservation -> !reservation.getReservationStart().isBefore(OffsetDateTime.now(ZoneOffset.UTC)))
                        .filter(reservation -> UPCOMING_STATUSES.contains(reservation.getStatus()))
                        .count())
                .build();
    }

    @Transactional(readOnly = true)
    public ReservationCapacityResponse getReservationCapacity(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer partySize
    ) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        ReservationSupport.TimeWindow window = reservationSupport.resolveCapacityWindow(from, to);
        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        List<RestaurantTable> rootTables = snapshot.tables().stream()
                .filter(table -> table.getMergedInto() == null)
                .toList();
        List<ReservationAvailabilitySupport.AvailableRootTable> availableRootTables = reservationAvailabilitySupport.loadAvailableRootTables(
                branch,
                snapshot,
                window.from(),
                window.to()
        );

        return ReservationCapacityResponse.builder()
                .branchId(branchId)
                .from(window.from())
                .to(window.to())
                .totalRootTables(rootTables.size())
                .availableRootTables(availableRootTables.size())
                .totalSeats(rootTables.stream()
                        .mapToInt(table -> restaurantTableSupport.effectiveCapacity(
                                table,
                                snapshot.childrenByParentId().getOrDefault(table.getId(), List.of())
                        ))
                        .sum())
                .availableSeats(availableRootTables.stream()
                        .mapToInt(ReservationAvailabilitySupport.AvailableRootTable::effectiveCapacity)
                        .sum())
                .maxSingleTableCapacity(rootTables.stream()
                        .mapToInt(table -> restaurantTableSupport.effectiveCapacity(
                                table,
                                snapshot.childrenByParentId().getOrDefault(table.getId(), List.of())
                        ))
                        .max()
                        .orElse(0))
                .maxAvailableTableCapacity(availableRootTables.stream()
                        .mapToInt(ReservationAvailabilitySupport.AvailableRootTable::effectiveCapacity)
                        .max()
                        .orElse(0))
                .requestedPartySize(partySize)
                .canAccommodateRequestedPartySize(partySize == null
                        ? null
                        : availableRootTables.stream().anyMatch(table -> table.effectiveCapacity() >= partySize)
                        || !reservationAvailabilitySupport.availabilityOptionsForBranch(
                                branch,
                                window.from(),
                                window.to(),
                                partySize,
                                1
                        ).isEmpty())
                .build();
    }

    @Transactional(readOnly = true)
    public ReservationValidationResponse validateReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            ReservationValidationRequest request
    ) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        reservationSupport.validateReservationWindow(request.getReservationStart(), request.getReservationEnd());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request.getTableIds() != null && !request.getTableIds().isEmpty()) {
            try {
                reservationAvailabilitySupport.validateTableSelection(
                        branch,
                        null,
                        request.getReservationStart(),
                        request.getReservationEnd(),
                        request.getPartySize(),
                        request.getTableIds(),
                        request.getPrimaryTableId()
                );
            } catch (AuthException ex) {
                errors.add(ex.getMessage());
            }
        } else {
            warnings.add("No tableIds were provided, validation checked availability only");
        }

        List<ReservationAvailabilityOptionResponse> suggestions = reservationAvailabilitySupport.availabilityOptionsForBranch(
                branch,
                request.getReservationStart(),
                request.getReservationEnd(),
                request.getPartySize(),
                DEFAULT_RECOMMENDATION_LIMIT
        );

        if (suggestions.isEmpty()) {
            errors.add("No available table combination can accommodate the requested party size in the requested window");
        }

        return ReservationValidationResponse.builder()
                .valid(errors.isEmpty())
                .errors(List.copyOf(errors))
                .warnings(List.copyOf(warnings))
                .suggestions(suggestions)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ReservationTableAssignmentResponse> getReservationTables(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        reservationSupport.requireReservation(restaurantId, reservationId);
        return reservationTableAssignmentRepository.findAllByReservation_IdOrderByPrimaryAssignmentDescAssignedAtAsc(reservationId).stream()
                .map(reservationSupport::toTableAssignmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationStatusHistoryResponse> getStatusHistory(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        reservationSupport.requireReservation(restaurantId, reservationId);
        return reservationSupport.mapStatusHistory(
                reservationStatusHistoryRepository.findAllByReservation_IdOrderByChangedAtDesc(reservationId)
        );
    }

    @Transactional(readOnly = true)
    public List<ReservationTimelineEventResponse> getTimeline(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);

        List<ReservationTimelineEventResponse> timeline = new ArrayList<>();
        timeline.add(ReservationTimelineEventResponse.builder()
                .id(reservation.getId())
                .type("CREATED")
                .occurredAt(reservation.getCreatedAt())
                .actorId(reservation.getCreatedBy())
                .message("Reservation created")
                .build());

        reservationStatusHistoryRepository.findAllByReservation_IdOrderByChangedAtDesc(reservationId).forEach(history ->
                timeline.add(ReservationTimelineEventResponse.builder()
                        .id(history.getId())
                        .type("STATUS_CHANGE")
                        .occurredAt(history.getChangedAt())
                        .actorId(history.getChangedBy())
                        .message(history.getReason() == null ? "Status changed to " + history.getNewStatus() : history.getReason())
                        .oldStatus(history.getOldStatus())
                        .newStatus(history.getNewStatus())
                        .build())
        );

        reservationTableAssignmentRepository.findAllByReservation_IdOrderByPrimaryAssignmentDescAssignedAtAsc(reservationId).forEach(assignment ->
                timeline.add(ReservationTimelineEventResponse.builder()
                        .id(assignment.getId())
                        .type("TABLE_ASSIGNED")
                        .occurredAt(assignment.getAssignedAt())
                        .actorId(assignment.getAssignedBy())
                        .message("Assigned table " + assignment.getRestaurantTable().getTableNumber())
                        .relatedTableId(assignment.getRestaurantTable().getId())
                        .build())
        );

        reservationNoteRepository.findAllByReservation_IdOrderByCreatedAtAsc(reservationId).forEach(note ->
                timeline.add(ReservationTimelineEventResponse.builder()
                        .id(note.getId())
                        .type("NOTE_ADDED")
                        .occurredAt(note.getCreatedAt())
                        .actorId(note.getCreatedBy())
                        .message(note.getNote())
                        .relatedNoteId(note.getId())
                        .build())
        );

        return timeline.stream()
                .sorted(Comparator.comparing(ReservationTimelineEventResponse::getOccurredAt).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationAuditResponse getAudit(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);

        return reservationSupport.toAuditResponse(
                reservation,
                reservationSupport.mapStatusHistory(
                        reservationStatusHistoryRepository.findAllByReservation_IdOrderByChangedAtDesc(reservationId)
                ),
                reservationSupport.mapNotes(
                        reservationNoteRepository.findAllByReservation_IdOrderByCreatedAtAsc(reservationId)
                ),
                reservationTableAssignmentRepository.findAllByReservation_IdOrderByPrimaryAssignmentDescAssignedAtAsc(reservationId)
                        .stream()
                        .map(reservationSupport::toTableAssignmentResponse)
                        .toList()
        );
    }

    private List<Reservation> loadBranchReservations(UUID branchId, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return reservationRepository.findAllByBranch_IdOrderByReservationStartAsc(branchId);
        }
        reservationSupport.requireCompleteWindow(from, to);
        return reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(branchId, from, to);
    }
}
