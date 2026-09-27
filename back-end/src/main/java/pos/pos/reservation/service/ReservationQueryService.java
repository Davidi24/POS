package pos.pos.reservation.service;

import pos.pos.utils.NormalizationUtils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.ReservationAuditResponse;
import pos.pos.reservation.dto.ReservationDetailsResponse;
import pos.pos.reservation.dto.ReservationDepositResponse;
import pos.pos.reservation.dto.ReservationNoteResponse;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.dto.ReservationAvailabilitySearchRequest;
import pos.pos.reservation.dto.ReservationCapacityResponse;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.ReservationStatusHistoryResponse;
import pos.pos.reservation.dto.ReservationSummaryResponse;
import pos.pos.reservation.dto.ReservationTableAssignmentResponse;
import pos.pos.reservation.dto.ReservationTimelineEventResponse;
import pos.pos.common.dto.PageResponse;
import pos.pos.reservation.dto.ReservationValidationRequest;
import pos.pos.reservation.dto.ReservationValidationResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationTableAssignment;
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
    private static final int MAX_UPCOMING_LIMIT = 100;
    private static final int DEFAULT_RESERVATION_PAGE_SIZE = 100;
    private static final int MAX_RESERVATION_PAGE_SIZE = 100;
    // Guests still expected: the arrivals feed and the "to arrive" numbers.
    private static final EnumSet<ReservationStatus> ARRIVING_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED
    );
    // Booked, not cancelled or no-show: what the day's reservation and guest totals count.
    private static final EnumSet<ReservationStatus> PRESENT_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.SEATED,
            ReservationStatus.COMPLETED
    );
    // Still holding (or about to hold) a table: what "without a table" counts.
    private static final EnumSet<ReservationStatus> NEEDS_TABLE_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.SEATED
    );
    // "Arriving soon": a guest up to 15 minutes late still counts, up to 2 hours ahead.
    private static final java.time.Duration ARRIVING_GRACE = java.time.Duration.ofMinutes(15);
    private static final java.time.Duration ARRIVING_SOON_WINDOW = java.time.Duration.ofHours(2);
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
    private final ReservationRuleResolver reservationRuleResolver;
    private final ReservationPolicy reservationPolicy;
    private final pos.pos.reservation.repository.ReservationEventRepository reservationEventRepository;

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return reservationSupport.toResponses(reservationRepository.findAllByRestaurant_IdOrderByReservationStartDesc(restaurantId));
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return reservationSupport.toResponse(reservationSupport.requireReservation(restaurantId, reservationId));
    }

    @Transactional(readOnly = true)
    public ReservationDetailsResponse getReservationDetails(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        List<ReservationStatusHistoryResponse> history = reservationSupport.mapStatusHistory(
                reservationStatusHistoryRepository.findAllByReservation_IdOrderByChangedAtDesc(reservationId)
        );
        List<ReservationNoteResponse> notes = reservationSupport.mapNotes(
                reservationNoteRepository.findAllByReservation_IdOrderByCreatedAtAsc(reservationId)
        );
        List<ReservationTableAssignment> assignmentEntities = reservationTableAssignmentRepository
                .findAllByReservation_IdOrderByPrimaryAssignmentDescAssignedAtAsc(reservationId);
        List<ReservationTableAssignmentResponse> assignments = assignmentEntities.stream()
                .map(reservationSupport::toTableAssignmentResponse)
                .toList();
        ReservationAuditResponse audit = reservationSupport.toAuditResponse(
                reservation,
                history,
                notes,
                assignments
        );
        ReservationDepositResponse deposit = reservationSupport.toDepositResponse(reservation);
        ReservationResponse response = reservationSupport.toResponse(reservation);

        List<ReservationTimelineEventResponse> timeline = new ArrayList<>();
        timeline.add(ReservationTimelineEventResponse.builder()
                .id(reservation.getId())
                .type("CREATED")
                .occurredAt(reservation.getCreatedAt())
                .actorId(reservation.getCreatedBy())
                .message("Reservation created")
                .build());
        history.forEach(entry -> timeline.add(ReservationTimelineEventResponse.builder()
                .id(entry.getId())
                .type("STATUS_CHANGE")
                .occurredAt(entry.getChangedAt())
                .actorId(entry.getChangedBy())
                .message(entry.getReason() == null ? "Status changed to " + entry.getNewStatus() : entry.getReason())
                .oldStatus(entry.getOldStatus())
                .newStatus(entry.getNewStatus())
                .build()));
        assignmentEntities.forEach(assignment -> timeline.add(ReservationTimelineEventResponse.builder()
                        .id(assignment.getId())
                        .type("TABLE_ASSIGNED")
                        .occurredAt(assignment.getAssignedAt())
                        .actorId(assignment.getAssignedBy())
                        .message("Assigned table " + assignment.getRestaurantTable().getTableNumber())
                        .relatedTableId(assignment.getRestaurantTable().getId())
                        .build()));
        notes.forEach(note -> timeline.add(ReservationTimelineEventResponse.builder()
                        .id(note.getId())
                        .type("NOTE_ADDED")
                        .occurredAt(note.getCreatedAt())
                        .actorId(note.getCreatedBy())
                        .message(note.getNote())
                        .relatedNoteId(note.getId())
                        .build()));
        timeline.addAll(eventTimeline(reservationId));
        timeline.sort(Comparator.comparing(ReservationTimelineEventResponse::getOccurredAt).reversed());

        return ReservationDetailsResponse.builder()
                .reservation(response)
                .audit(audit)
                .timeline(timeline)
                .deposit(deposit)
                .build();
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

        return reservationSupport.toResponses(reservations.stream()
                .filter(reservation -> status == null || reservation.getStatus() == status)
                .filter(reservation -> customerId == null || Objects.equals(
                        reservation.getCustomer() == null ? null : reservation.getCustomer().getId(),
                        customerId
                ))
                .toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getBranchReservationCalendar(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
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

        return getReservationPage(branchId, resolvedFrom, resolvedTo, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getTodayReservations(Authentication authentication, UUID restaurantId, UUID branchId, int page, int size) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        ReservationSupport.TimeWindow window = reservationSupport.dayWindow(
                branch,
                LocalDate.now(reservationSupport.restaurantZone(branch.getRestaurant()))
        );
        return getReservationPage(branchId, window.from(), window.to(), page, size);
    }

    // Guests still to come (pending or confirmed) from a moment on, soonest first, one page at a time. Defaults to
    // 15 minutes ago so late guests stay listed. With a floor, only that floor's bookings plus those without a table.
    @Transactional(readOnly = true)
    public PageResponse<ReservationResponse> getArrivals(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            String floor,
            int page,
            int size
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        OffsetDateTime resolvedFrom = from != null ? from : OffsetDateTime.now(ZoneOffset.UTC).minus(ARRIVING_GRACE);
        String onlyFloor = NormalizationUtils.normalize(floor);
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), resolvePageSize(size));
        Page<UUID> ids = onlyFloor == null
                ? reservationRepository.findUpcomingIdPage(branchId, ARRIVING_STATUSES, resolvedFrom, pageRequest)
                : reservationRepository.findUpcomingIdPageOnFloor(branchId, ARRIVING_STATUSES, resolvedFrom, onlyFloor, pageRequest);
        return toReservationPage(ids);
    }

    private int resolvePageSize(int size) {
        return size <= 0 ? DEFAULT_RESERVATION_PAGE_SIZE : Math.min(size, MAX_RESERVATION_PAGE_SIZE);
    }

    private PageResponse<ReservationResponse> getReservationPage(UUID branchId, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        Page<UUID> ids = reservationRepository.findReservationIdsForBranchInWindow(
                branchId, from, to, PageRequest.of(Math.max(0, page), resolvePageSize(size))
        );
        return toReservationPage(ids);
    }

    // Loads the full rows for one page of ids, keeping the page's order.
    private PageResponse<ReservationResponse> toReservationPage(Page<UUID> ids) {
        if (ids.isEmpty()) {
            return PageResponse.from(new PageImpl<>(List.of(), ids.getPageable(), ids.getTotalElements()));
        }
        var byId = reservationRepository.findAllByIdInOrderByReservationStartAsc(ids.getContent()).stream()
                .collect(java.util.stream.Collectors.toMap(Reservation::getId, reservation -> reservation));
        List<ReservationResponse> items = reservationSupport.toResponses(ids.getContent().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList());
        return PageResponse.from(new PageImpl<>(items, ids.getPageable(), ids.getTotalElements()));
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getUpcomingReservations(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            Integer limit
    ) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        int resolvedLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, MAX_UPCOMING_LIMIT);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<UUID> ids = reservationRepository.findUpcomingIds(branchId, UPCOMING_STATUSES, now, PageRequest.of(0, resolvedLimit));
        if (ids.isEmpty()) {
            return List.of();
        }
        return reservationSupport.toResponses(reservationRepository.findAllByIdInOrderByReservationStartAsc(ids));
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
            OffsetDateTime to,
            String floor
    ) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        ReservationSupport.TimeWindow window = reservationSupport.resolveSummaryWindow(branch, from, to);
        String onlyFloor = NormalizationUtils.normalize(floor);
        List<Reservation> reservations = reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(
                branchId,
                window.from(),
                window.to()
        ).stream().filter(reservation -> onlyFloor == null || isOnFloor(reservation, onlyFloor)).toList();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime soonFrom = now.minus(ARRIVING_GRACE);
        OffsetDateTime soonTo = now.plus(ARRIVING_SOON_WINDOW);
        List<Reservation> arrivingSoon = reservations.stream()
                .filter(reservation -> ARRIVING_STATUSES.contains(reservation.getStatus()))
                .filter(reservation -> !reservation.getReservationStart().isBefore(soonFrom)
                        && !reservation.getReservationStart().isAfter(soonTo))
                .toList();

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
                .expiredCount(reservationSupport.countByStatus(reservations, ReservationStatus.EXPIRED))
                .attendanceNotConfirmedCount((int) reservations.stream()
                        .filter(reservation -> reservation.getStatus() == ReservationStatus.CONFIRMED && reservation.getAttendanceConfirmedAt() == null)
                        .count())
                .notConfirmedDueCount((int) reservations.stream()
                        .filter(reservation -> "NOT_CONFIRMED".equals(reservationSupport.attendance(reservation, now)))
                        .count())
                .bigGroupCount((int) reservations.stream()
                        .filter(reservation -> ARRIVING_STATUSES.contains(reservation.getStatus()))
                        .filter(reservation -> reservation.getPartySize() >= reservationPolicy.values(branch.getRestaurant()).approvalGroupSize())
                        .count())
                .needsReviewCount((int) reservations.stream()
                        .filter(reservation -> reservationSupport.reviewReason(reservation, now) != null)
                        .count()
                        + (int) reservationRepository.countByBranch_IdAndStatusInAndReservationStartLessThan(
                                branchId, EnumSet.of(ReservationStatus.CHECKED_IN, ReservationStatus.SEATED), window.from()))
                .upcomingCount((int) reservationRepository.countByBranch_IdAndStatusInAndReservationStartGreaterThanEqual(
                        branchId, UPCOMING_STATUSES, OffsetDateTime.now(ZoneOffset.UTC)))
                .presentGuests(guests(reservations, PRESENT_STATUSES))
                .guestsToArrive(guests(reservations, ARRIVING_STATUSES))
                .unassignedCount((int) reservations.stream()
                        .filter(reservation -> NEEDS_TABLE_STATUSES.contains(reservation.getStatus()))
                        .filter(reservation -> reservation.getTableAssignments().isEmpty())
                        .count())
                .arrivingSoonCount(arrivingSoon.size())
                .arrivingSoonGuests(arrivingSoon.stream().mapToInt(Reservation::getPartySize).sum())
                .build();
    }

    // Same floor rule as the arrivals feed: one of its tables is on the floor, or it has no table yet.
    private static boolean isOnFloor(Reservation reservation, String floor) {
        return reservation.getTableAssignments().isEmpty() || reservation.getTableAssignments().stream()
                .anyMatch(assignment -> assignment.getRestaurantTable() != null
                        && floor.equalsIgnoreCase(assignment.getRestaurantTable().getFloor()));
    }

    private static int guests(List<Reservation> reservations, EnumSet<ReservationStatus> statuses) {
        return reservations.stream()
                .filter(reservation -> statuses.contains(reservation.getStatus()))
                .mapToInt(Reservation::getPartySize)
                .sum();
    }

    @Transactional(readOnly = true)
    public pos.pos.reservation.dto.ReservationSettingsResponse getReservationSettings(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId
    ) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ReservationPolicy.Values values = reservationPolicy.values(branch.getRestaurant());
        var response = pos.pos.reservation.dto.ReservationSettingsResponse.builder()
                .branchId(branchId)
                .timezone(reservationSupport.restaurantZone(branch.getRestaurant()).getId())
                .defaultDurationMinutes(reservationRuleResolver.durationMinutes(branch, now))
                .bufferMinutes((int) reservationRuleResolver.buffer(branch, now).toMinutes())
                .largeGroupFrom(values.largeGroupFrom())
                .largeGroupExtraMinutes(values.largeGroupExtraMinutes())
                .approvalGroupSize(values.approvalGroupSize())
                .holdMinutes(values.holdMinutes())
                .holdWarningMinutes(values.holdWarningMinutes())
                .lateAfterMinutes(values.lateAfterMinutes())
                .checkInOpensMinutes(values.checkInOpensMinutes())
                .reopenWindowMinutes(values.reopenWindowMinutes())
                .undoSeatMinutes(values.undoSeatMinutes())
                .runningLateMaxMinutes(values.runningLateMaxMinutes())
                .sameDayConfirmMinutes(values.sameDayConfirmMinutes())
                .attendanceCallMinutes(values.attendanceCallMinutes())
                .confirmReminderTime(values.confirmReminderTime() == null ? null : values.confirmReminderTime().toString())
                .noShowWarningFrom(values.noShowWarningFrom())
                .serviceDayStartHour(ReservationPolicy.SERVICE_DAY_START_HOUR);
        reservationRuleResolver.activeRule(branch, now).ifPresent(rule -> response
                .ruleName(rule.getRuleName())
                .minPartySize(rule.getMinPartySize())
                .maxPartySize(rule.getMaxPartySize())
                .advanceBookingDays(rule.getAdvanceBookingDays()));
        return response.build();
    }

    // Late guests keep their end time: do they still fit at their tables, and where else could they sit.
    @Transactional(readOnly = true)
    public pos.pos.reservation.dto.ReservationSeatingCheckResponse getSeatingCheck(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Reservation reservation = reservationSupport.requireReservation(restaurantId, reservationId);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = reservation.getReservationStart().isAfter(now) ? reservation.getReservationStart() : now;
        OffsetDateTime end = reservation.getReservationEnd();
        long minutesLeft = Math.max(0, java.time.Duration.between(from, end).toMinutes());

        List<UUID> tableIds = reservation.getTableAssignments().stream()
                .map(assignment -> assignment.getRestaurantTable().getId())
                .toList();
        Reservation next = tableIds.isEmpty() ? null : reservationTableAssignmentRepository
                .findUpcomingByTableIds(tableIds, EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN), from)
                .stream()
                .map(ReservationTableAssignment::getReservation)
                .filter(other -> !other.getId().equals(reservation.getId()))
                .filter(other -> !other.getReservationStart().isBefore(reservation.getReservationStart()))
                .min(java.util.Comparator.comparing(Reservation::getReservationStart))
                .orElse(null);
        java.time.Duration buffer = reservationRuleResolver.buffer(reservation.getBranch(), from);
        boolean fits = !tableIds.isEmpty() && (next == null || !next.getReservationStart().minus(buffer).isBefore(end));
        List<ReservationAvailabilityOptionResponse> alternatives = fits || !end.isAfter(from)
                ? List.of()
                : reservationAvailabilitySupport.availabilityOptionsForBranch(reservation.getBranch(), from, end, reservation.getPartySize(), 3);
        return pos.pos.reservation.dto.ReservationSeatingCheckResponse.builder()
                .minutesLeft((int) minutesLeft)
                .nextBookingStart(next == null ? null : next.getReservationStart())
                .nextBookingName(next == null ? null : next.getContactName())
                .fitsAtTables(fits)
                .alternatives(alternatives)
                .build();
    }

    @Transactional(readOnly = true)
    public ReservationCapacityResponse getReservationCapacity(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to,
            Integer partySize,
            String floor
    ) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        ReservationSupport.TimeWindow window = reservationSupport.resolveCapacityWindow(from, to);
        // Optional: count only one floor, so the numbers match the floor plan on screen.
        String onlyFloor = NormalizationUtils.normalize(floor);
        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        List<RestaurantTable> rootTables = snapshot.tables().stream()
                .filter(table -> table.getMergedInto() == null)
                .filter(table -> onlyFloor == null || onlyFloor.equalsIgnoreCase(table.getFloor()))
                .toList();
        List<ReservationAvailabilitySupport.AvailableRootTable> availableRootTables = reservationAvailabilitySupport.loadAvailableRootTables(
                branch,
                snapshot,
                window.from(),
                window.to()
        ).stream()
                .filter(available -> onlyFloor == null || onlyFloor.equalsIgnoreCase(available.table().getFloor()))
                .toList();

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

        timeline.addAll(eventTimeline(reservationId));
        return timeline.stream()
                .sorted(Comparator.comparing(ReservationTimelineEventResponse::getOccurredAt).reversed())
                .toList();
    }

    // Changes that aren't a status change: "Table held until 19:45 · Called: traffic", "1 → 2 of 2 arrived".
    private List<ReservationTimelineEventResponse> eventTimeline(UUID reservationId) {
        return reservationEventRepository.findAllByReservation_IdOrderByCreatedAtAsc(reservationId).stream()
                .map(event -> ReservationTimelineEventResponse.builder()
                        .id(event.getId())
                        .type(event.getType().name())
                        .occurredAt(event.getCreatedAt())
                        .actorId(event.getActorId())
                        .message(event.getReason() == null ? event.getDetail() : event.getDetail() + " · " + event.getReason())
                        .build())
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
