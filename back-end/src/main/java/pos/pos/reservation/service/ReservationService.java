package pos.pos.reservation.service;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.customer.entity.Customer;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.customer.CustomerNotFoundException;
import pos.pos.exception.reservation.ReservationNoteNotFoundException;
import pos.pos.exception.reservation.ReservationNotFoundException;
import pos.pos.reservation.dto.PublicReservationRequest;
import pos.pos.reservation.dto.PublicReservationResponse;
import pos.pos.reservation.dto.PublicTableLookupResponse;
import pos.pos.reservation.dto.ReservationActionRequest;
import pos.pos.reservation.dto.ReservationAuditResponse;
import pos.pos.reservation.dto.ReservationAvailabilityOptionResponse;
import pos.pos.reservation.dto.ReservationAvailabilitySearchRequest;
import pos.pos.reservation.dto.ReservationCapacityResponse;
import pos.pos.reservation.dto.ReservationDepositResponse;
import pos.pos.reservation.dto.ReservationNoteRequest;
import pos.pos.reservation.dto.ReservationNoteResponse;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.ReservationStatusHistoryResponse;
import pos.pos.reservation.dto.ReservationSummaryResponse;
import pos.pos.reservation.dto.ReservationTableAssignmentResponse;
import pos.pos.reservation.dto.ReservationTimelineEventResponse;
import pos.pos.reservation.dto.ReservationValidationRequest;
import pos.pos.reservation.dto.ReservationValidationResponse;
import pos.pos.reservation.dto.UpdateReservationDepositRequest;
import pos.pos.reservation.dto.UpdateReservationRequest;
import pos.pos.reservation.dto.UpdateReservationTablesRequest;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.ReservationNote;
import pos.pos.reservation.entity.ReservationStatusHistory;
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.reservation.enums.ReservationDepositStatus;
import pos.pos.reservation.enums.ReservationSource;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.mapper.ReservationMapper;
import pos.pos.reservation.repository.ReservationNoteRepository;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.ReservationStatusHistoryRepository;
import pos.pos.reservation.repository.ReservationTableAssignmentRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.service.RestaurantTableSupport;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@lombok.RequiredArgsConstructor
public class ReservationService {

    private static final EnumSet<ReservationStatus> BLOCKING_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.SEATED
    );
    private static final EnumSet<ReservationStatus> UPCOMING_STATUSES = EnumSet.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.CHECKED_IN,
            ReservationStatus.SEATED
    );
    private static final int DEFAULT_AVAILABILITY_LIMIT = 10;
    private static final int DEFAULT_RECOMMENDATION_LIMIT = 3;
    private static final int MAX_COMBINATION_DEPTH = 4;

    private final RestaurantScopeService restaurantScopeService;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationStatusHistoryRepository reservationStatusHistoryRepository;
    private final ReservationTableAssignmentRepository reservationTableAssignmentRepository;
    private final ReservationNoteRepository reservationNoteRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantTableSupport restaurantTableSupport;
    private final ReservationMapper reservationMapper;

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return reservationRepository.findAllByRestaurant_IdOrderByReservationStartDesc(restaurantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReservationResponse createReservation(Authentication authentication, UUID restaurantId, ReservationRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Branch branch = resolveManagedBranch(authentication, restaurantId, request.getBranchId());
        Customer customer = resolveCustomer(restaurantId, request.getCustomerId());

        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setBranch(branch);
        reservation.setCustomer(customer);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setCreatedBy(actorId);
        reservation.setUpdatedBy(actorId);

        applyReservationRequest(reservation, request, actorId, true);
        addStatusHistory(reservation, null, ReservationStatus.PENDING, "Reservation created", actorId);

        if (request.getInitialTableIds() != null) {
            replaceReservationTables(reservation, request.getInitialTableIds(), request.getPrimaryTableId(), actorId);
        }

        return toResponse(saveReservation(reservation));
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return toResponse(requireReservation(restaurantId, reservationId));
    }

    @Transactional
    public ReservationResponse updateReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        Branch branch = resolveManagedBranch(authentication, restaurantId, request.getBranchId(), reservation.getBranch().getId());
        Customer customer = resolveCustomer(restaurantId, request.getCustomerId());

        reservation.setBranch(branch);
        reservation.setCustomer(customer);
        reservation.setUpdatedBy(actorId);

        applyReservationRequest(reservation, request, actorId, false);
        if (request.getInitialTableIds() != null) {
            replaceReservationTables(reservation, request.getInitialTableIds(), request.getPrimaryTableId(), actorId);
        }

        return toResponse(saveReservation(reservation));
    }

    @Transactional
    public ReservationResponse patchReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UpdateReservationRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        if (request.getBranchId() != null) {
            reservation.setBranch(resolveManagedBranch(authentication, restaurantId, request.getBranchId()));
        }
        if (request.getCustomerId() != null) {
            reservation.setCustomer(resolveCustomer(restaurantId, request.getCustomerId()));
        }

        applyReservationPatch(reservation, request, actorId);
        if (request.getTableIds() != null) {
            replaceReservationTables(reservation, request.getTableIds(), request.getPrimaryTableId(), actorId);
        }

        return toResponse(saveReservation(reservation));
    }

    @Transactional
    public void deleteReservation(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        reservationRepository.delete(requireReservation(restaurantId, reservationId));
        reservationRepository.flush();
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
                .map(this::toResponse)
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
            ZoneId zoneId = restaurantZone(branch.getRestaurant());
            LocalDate currentDate = LocalDate.now(zoneId);
            resolvedFrom = currentDate.withDayOfMonth(1).atStartOfDay(zoneId).toOffsetDateTime();
            resolvedTo = currentDate.withDayOfMonth(1).plusMonths(1).atStartOfDay(zoneId).toOffsetDateTime();
        } else {
            requireCompleteWindow(resolvedFrom, resolvedTo);
        }

        return reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(
                        branchId,
                        resolvedFrom,
                        resolvedTo
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getTodayReservations(Authentication authentication, UUID restaurantId, UUID branchId) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        TimeWindow window = dayWindow(branch, LocalDate.now(restaurantZone(branch.getRestaurant())));
        return reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(
                        branchId,
                        window.from(),
                        window.to()
                ).stream()
                .map(this::toResponse)
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
                .map(this::toResponse)
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
        return availabilityOptionsForBranch(
                restaurantScopeService.requireExistingBranch(restaurantId, branchId),
                request.getReservationStart(),
                request.getReservationEnd(),
                request.getPartySize(),
                resolveAvailabilityLimit(request.getMaxOptions(), DEFAULT_AVAILABILITY_LIMIT)
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
        return availabilityOptionsForBranch(
                restaurantScopeService.requireExistingBranch(restaurantId, branchId),
                request.getReservationStart(),
                request.getReservationEnd(),
                request.getPartySize(),
                resolveAvailabilityLimit(request.getMaxOptions(), DEFAULT_RECOMMENDATION_LIMIT)
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
        TimeWindow window = resolveSummaryWindow(branch, from, to);
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
                .pendingCount(countByStatus(reservations, ReservationStatus.PENDING))
                .confirmedCount(countByStatus(reservations, ReservationStatus.CONFIRMED))
                .checkedInCount(countByStatus(reservations, ReservationStatus.CHECKED_IN))
                .seatedCount(countByStatus(reservations, ReservationStatus.SEATED))
                .completedCount(countByStatus(reservations, ReservationStatus.COMPLETED))
                .cancelledCount(countByStatus(reservations, ReservationStatus.CANCELLED))
                .noShowCount(countByStatus(reservations, ReservationStatus.NO_SHOW))
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
        TimeWindow window = resolveCapacityWindow(from, to);
        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(restaurantId, branchId);
        List<RestaurantTable> rootTables = snapshot.tables().stream()
                .filter(table -> table.getMergedInto() == null)
                .toList();
        List<AvailableRootTable> availableRootTables = loadAvailableRootTables(branch, snapshot, window.from(), window.to(), null);

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
                .availableSeats(availableRootTables.stream().mapToInt(AvailableRootTable::effectiveCapacity).sum())
                .maxSingleTableCapacity(rootTables.stream()
                        .mapToInt(table -> restaurantTableSupport.effectiveCapacity(
                                table,
                                snapshot.childrenByParentId().getOrDefault(table.getId(), List.of())
                        ))
                        .max()
                        .orElse(0))
                .maxAvailableTableCapacity(availableRootTables.stream().mapToInt(AvailableRootTable::effectiveCapacity).max().orElse(0))
                .requestedPartySize(partySize)
                .canAccommodateRequestedPartySize(partySize == null
                        ? null
                        : availableRootTables.stream().anyMatch(table -> table.effectiveCapacity() >= partySize)
                        || !buildAvailabilityOptions(availableRootTables, partySize, 1).isEmpty())
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
        validateReservationWindow(request.getReservationStart(), request.getReservationEnd());

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request.getTableIds() != null && !request.getTableIds().isEmpty()) {
            try {
                validateTableSelection(
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

        List<ReservationAvailabilityOptionResponse> suggestions = availabilityOptionsForBranch(
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

    @Transactional
    public ReservationResponse confirmReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.CONFIRMED, request);
    }

    @Transactional
    public ReservationResponse cancelReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.CANCELLED, request);
    }

    @Transactional
    public ReservationResponse checkInReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.CHECKED_IN, request);
    }

    @Transactional
    public ReservationResponse seatReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.SEATED, request);
    }

    @Transactional
    public ReservationResponse completeReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.COMPLETED, request);
    }

    @Transactional
    public ReservationResponse markNoShow(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.NO_SHOW, request);
    }

    @Transactional
    public ReservationResponse reopenReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationActionRequest request
    ) {
        return transitionReservation(authentication, restaurantId, reservationId, ReservationStatus.PENDING, request);
    }

    @Transactional(readOnly = true)
    public List<ReservationTableAssignmentResponse> getReservationTables(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        requireReservation(restaurantId, reservationId);
        return reservationTableAssignmentRepository.findAllByReservation_IdOrderByPrimaryAssignmentDescAssignedAtAsc(reservationId).stream()
                .map(reservationMapper::toTableAssignmentResponse)
                .toList();
    }

    @Transactional
    public List<ReservationTableAssignmentResponse> updateReservationTables(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UpdateReservationTablesRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        replaceReservationTables(
                reservation,
                request.getTableIds(),
                request.getPrimaryTableId(),
                restaurantScopeService.currentUserId(authentication)
        );
        saveReservation(reservation);
        return mapAssignments(reservation);
    }

    @Transactional
    public ReservationTableAssignmentResponse addReservationTable(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UUID tableId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);

        if (reservation.getTableAssignments().stream()
                .anyMatch(assignment -> Objects.equals(assignment.getRestaurantTable().getId(), tableId))) {
            throw new AuthException("Table is already assigned to this reservation", HttpStatus.CONFLICT);
        }

        List<UUID> selectedTableIds = reservation.getTableAssignments().stream()
                .map(assignment -> assignment.getRestaurantTable().getId())
                .collect(Collectors.toCollection(ArrayList::new));
        selectedTableIds.add(tableId);

        SelectionValidationResult validation = validateTableSelection(
                reservation.getBranch(),
                reservation.getId(),
                reservation.getReservationStart(),
                reservation.getReservationEnd(),
                reservation.getPartySize(),
                selectedTableIds,
                currentPrimaryTableId(reservation)
        );

        RestaurantTable selectedTable = validation.selectedTables().get(tableId);
        ReservationTableAssignment assignment = new ReservationTableAssignment();
        assignment.setRestaurantTable(selectedTable);
        assignment.setPrimaryAssignment(reservation.getTableAssignments().stream().noneMatch(ReservationTableAssignment::isPrimaryAssignment));
        assignment.setAssignedBy(restaurantScopeService.currentUserId(authentication));
        reservation.addTableAssignment(assignment);
        saveReservation(reservation);

        return reservationMapper.toTableAssignmentResponse(
                reservationTableAssignmentRepository.findByReservation_IdAndRestaurantTable_Id(reservationId, tableId)
                        .orElseThrow(() -> new AuthException("Table assignment could not be created", HttpStatus.INTERNAL_SERVER_ERROR))
        );
    }

    @Transactional
    public void deleteReservationTable(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UUID tableId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);

        ReservationTableAssignment assignment = reservation.getTableAssignments().stream()
                .filter(item -> Objects.equals(item.getRestaurantTable().getId(), tableId))
                .findFirst()
                .orElseThrow(() -> new AuthException("Table is not assigned to this reservation", HttpStatus.NOT_FOUND));

        boolean removingPrimary = assignment.isPrimaryAssignment();
        reservation.removeTableAssignment(assignment);

        if (removingPrimary && !reservation.getTableAssignments().isEmpty()) {
            reservation.getTableAssignments().get(0).setPrimaryAssignment(true);
        }

        saveReservation(reservation);
    }

    @Transactional
    public List<ReservationTableAssignmentResponse> markPrimaryReservationTable(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UUID tableId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);

        ReservationTableAssignment assignment = reservation.getTableAssignments().stream()
                .filter(item -> Objects.equals(item.getRestaurantTable().getId(), tableId))
                .findFirst()
                .orElseThrow(() -> new AuthException("Table is not assigned to this reservation", HttpStatus.NOT_FOUND));

        reservation.getTableAssignments().forEach(item -> item.setPrimaryAssignment(false));
        assignment.setPrimaryAssignment(true);
        saveReservation(reservation);
        return mapAssignments(reservation);
    }

    @Transactional
    public List<ReservationTableAssignmentResponse> autoAssignReservationTable(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        List<ReservationAvailabilityOptionResponse> options = availabilityOptionsForBranch(
                reservation.getBranch(),
                reservation.getReservationStart(),
                reservation.getReservationEnd(),
                reservation.getPartySize(),
                1
        );

        if (options.isEmpty()) {
            throw new AuthException("No available table combination can be auto-assigned", HttpStatus.CONFLICT);
        }

        ReservationAvailabilityOptionResponse bestOption = options.get(0);
        replaceReservationTables(
                reservation,
                bestOption.getTableIds(),
                bestOption.getPrimaryTableId(),
                restaurantScopeService.currentUserId(authentication)
        );
        saveReservation(reservation);
        return mapAssignments(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationStatusHistoryResponse> getStatusHistory(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        requireReservation(restaurantId, reservationId);
        return reservationStatusHistoryRepository.findAllByReservation_IdOrderByChangedAtDesc(reservationId).stream()
                .map(reservationMapper::toStatusHistoryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationTimelineEventResponse> getTimeline(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);

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
                        .message(history.getReason() == null
                                ? "Status changed to " + history.getNewStatus()
                                : history.getReason())
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
        Reservation reservation = requireReservation(restaurantId, reservationId);

        return reservationMapper.toAuditResponse(
                reservation,
                getStatusHistory(authentication, restaurantId, reservationId),
                reservationNoteRepository.findAllByReservation_IdOrderByCreatedAtAsc(reservationId).stream()
                        .map(reservationMapper::toNoteResponse)
                        .toList(),
                getReservationTables(authentication, restaurantId, reservationId)
        );
    }

    @Transactional
    public ReservationNoteResponse addNote(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationNoteRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        ReservationNote note = new ReservationNote();
        note.setReservation(reservation);
        note.setNote(request.getNote());
        note.setCreatedBy(actorId);
        note.setUpdatedBy(actorId);

        return reservationMapper.toNoteResponse(saveReservationNote(note));
    }

    @Transactional
    public void deleteNote(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UUID noteId
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        requireReservation(restaurantId, reservationId);
        ReservationNote note = reservationNoteRepository.findByIdAndReservation_Id(noteId, reservationId)
                .orElseThrow(ReservationNoteNotFoundException::new);
        reservationNoteRepository.delete(note);
        reservationNoteRepository.flush();
    }

    @Transactional(readOnly = true)
    public ReservationDepositResponse getDeposit(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return reservationMapper.toDepositResponse(requireReservation(restaurantId, reservationId));
    }

    @Transactional
    public ReservationDepositResponse updateDeposit(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            UpdateReservationDepositRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        boolean depositRequired = request.getDepositRequired() != null
                ? request.getDepositRequired()
                : reservation.isDepositRequired();
        reservation.setDepositRequired(depositRequired);
        reservation.setDepositAmount(depositRequired ? request.getDepositAmount() : null);
        reservation.setDepositStatus(depositRequired
                ? normalizeDepositStatusForRequiredReservation(reservation.getDepositStatus())
                : ReservationDepositStatus.NOT_REQUIRED);

        saveReservation(reservation);
        return reservationMapper.toDepositResponse(reservation);
    }

    @Transactional
    public ReservationDepositResponse payDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        requireDepositConfigured(reservation);
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        reservation.setDepositStatus(ReservationDepositStatus.PAID);
        saveReservation(reservation);
        return reservationMapper.toDepositResponse(reservation);
    }

    @Transactional
    public ReservationDepositResponse refundDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        if (!EnumSet.of(ReservationDepositStatus.PAID, ReservationDepositStatus.PARTIALLY_PAID).contains(reservation.getDepositStatus())) {
            throw new AuthException("Deposit can only be refunded after payment", HttpStatus.BAD_REQUEST);
        }
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        reservation.setDepositStatus(ReservationDepositStatus.REFUNDED);
        saveReservation(reservation);
        return reservationMapper.toDepositResponse(reservation);
    }

    @Transactional
    public ReservationDepositResponse waiveDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        requireDepositConfigured(reservation);
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        reservation.setDepositStatus(ReservationDepositStatus.WAIVED);
        saveReservation(reservation);
        return reservationMapper.toDepositResponse(reservation);
    }

    @Transactional
    public ReservationDepositResponse forfeitDeposit(Authentication authentication, UUID restaurantId, UUID reservationId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        requireDepositConfigured(reservation);
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        reservation.setDepositStatus(ReservationDepositStatus.FORFEITED);
        saveReservation(reservation);
        return reservationMapper.toDepositResponse(reservation);
    }

    @Transactional(readOnly = true)
    public List<ReservationAvailabilityOptionResponse> getPublicAvailability(
            String restaurantSlug,
            String branchCode,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            Integer partySize,
            Integer maxOptions
    ) {
        Branch branch = requirePublicBranch(restaurantSlug, branchCode);
        if (partySize == null || partySize <= 0) {
            throw new AuthException("partySize must be greater than 0", HttpStatus.BAD_REQUEST);
        }
        return availabilityOptionsForBranch(
                branch,
                reservationStart,
                reservationEnd,
                partySize,
                resolveAvailabilityLimit(maxOptions, DEFAULT_AVAILABILITY_LIMIT)
        );
    }

    @Transactional
    public PublicReservationResponse createPublicReservation(
            String restaurantSlug,
            String branchCode,
            PublicReservationRequest request
    ) {
        Branch branch = requirePublicBranch(restaurantSlug, branchCode);
        Reservation reservation = new Reservation();
        reservation.setRestaurant(branch.getRestaurant());
        reservation.setBranch(branch);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setSource(ReservationSource.WEB);

        applyPublicReservationRequest(reservation, request);
        addStatusHistory(reservation, null, ReservationStatus.PENDING, "Reservation created", null);
        return reservationMapper.toPublicResponse(saveReservation(reservation), reservation.getTableAssignments());
    }

    @Transactional(readOnly = true)
    public PublicReservationResponse getPublicReservation(String reservationCode) {
        return reservationMapper.toPublicResponse(requirePublicReservation(reservationCode), requirePublicReservation(reservationCode).getTableAssignments());
    }

    @Transactional
    public PublicReservationResponse cancelPublicReservation(String reservationCode, ReservationActionRequest request) {
        Reservation reservation = requirePublicReservation(reservationCode);
        transitionReservation(reservation, ReservationStatus.CANCELLED, request == null ? null : request.getReason(), null);
        saveReservation(reservation);
        return reservationMapper.toPublicResponse(reservation, reservation.getTableAssignments());
    }

    @Transactional(readOnly = true)
    public PublicTableLookupResponse getPublicTable(String qrCodeValue) {
        RestaurantTable table = restaurantTableRepository.findFirstByQrCodeValueAndActiveTrue(qrCodeValue)
                .orElseThrow(() -> new AuthException("Table not found", HttpStatus.NOT_FOUND));
        assertPublicAvailability(table.getRestaurant(), table.getBranch());
        Map<UUID, List<RestaurantTable>> children = restaurantTableSupport.loadChildMap(table.getId());
        int effectiveCapacity = restaurantTableSupport.effectiveCapacity(
                table,
                children.getOrDefault(table.getId(), List.of())
        );
        return reservationMapper.toPublicTableLookupResponse(table, effectiveCapacity);
    }

    private ReservationResponse transitionReservation(
            Authentication authentication,
            UUID restaurantId,
            UUID reservationId,
            ReservationStatus targetStatus,
            ReservationActionRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Reservation reservation = requireReservation(restaurantId, reservationId);
        transitionReservation(
                reservation,
                targetStatus,
                request == null ? null : request.getReason(),
                restaurantScopeService.currentUserId(authentication)
        );
        reservation.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        return toResponse(saveReservation(reservation));
    }

    private void transitionReservation(
            Reservation reservation,
            ReservationStatus targetStatus,
            String reason,
            UUID actorId
    ) {
        ReservationStatus currentStatus = reservation.getStatus();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (targetStatus == ReservationStatus.CONFIRMED) {
            assertCurrentStatus(currentStatus, ReservationStatus.PENDING);
            reservation.setStatus(ReservationStatus.CONFIRMED);
            reservation.setConfirmedAt(now);
        } else if (targetStatus == ReservationStatus.CANCELLED) {
            if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED).contains(currentStatus)) {
                throw new AuthException("Reservation can only be cancelled from PENDING or CONFIRMED", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.CANCELLED);
            reservation.setCancelledAt(now);
            reservation.setCancellationReason(reason);
        } else if (targetStatus == ReservationStatus.CHECKED_IN) {
            if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED).contains(currentStatus)) {
                throw new AuthException("Reservation can only be checked in from PENDING or CONFIRMED", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.CHECKED_IN);
            if (reservation.getConfirmedAt() == null) {
                reservation.setConfirmedAt(now);
            }
            reservation.setCheckedInAt(now);
        } else if (targetStatus == ReservationStatus.SEATED) {
            if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.CHECKED_IN).contains(currentStatus)) {
                throw new AuthException("Reservation can only be seated from PENDING, CONFIRMED, or CHECKED_IN", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.SEATED);
            if (reservation.getConfirmedAt() == null) {
                reservation.setConfirmedAt(now);
            }
            if (reservation.getCheckedInAt() == null) {
                reservation.setCheckedInAt(now);
            }
            reservation.setSeatedAt(now);
        } else if (targetStatus == ReservationStatus.COMPLETED) {
            if (!EnumSet.of(ReservationStatus.CHECKED_IN, ReservationStatus.SEATED).contains(currentStatus)) {
                throw new AuthException("Reservation can only be completed from CHECKED_IN or SEATED", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.COMPLETED);
            if (reservation.getConfirmedAt() == null) {
                reservation.setConfirmedAt(now);
            }
            if (reservation.getCheckedInAt() == null) {
                reservation.setCheckedInAt(now);
            }
            reservation.setCompletedAt(now);
        } else if (targetStatus == ReservationStatus.NO_SHOW) {
            if (!EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED).contains(currentStatus)) {
                throw new AuthException("Reservation can only be marked no-show from PENDING or CONFIRMED", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.NO_SHOW);
            reservation.setNoShowAt(now);
        } else if (targetStatus == ReservationStatus.PENDING) {
            if (!EnumSet.of(ReservationStatus.CANCELLED, ReservationStatus.NO_SHOW).contains(currentStatus)) {
                throw new AuthException("Reservation can only be reopened from CANCELLED or NO_SHOW", HttpStatus.BAD_REQUEST);
            }
            reservation.setStatus(ReservationStatus.PENDING);
            reservation.setConfirmedAt(null);
            reservation.setCancelledAt(null);
            reservation.setCancellationReason(null);
            reservation.setCheckedInAt(null);
            reservation.setSeatedAt(null);
            reservation.setCompletedAt(null);
            reservation.setNoShowAt(null);
        } else {
            throw new AuthException("Unsupported reservation status transition", HttpStatus.BAD_REQUEST);
        }

        addStatusHistory(reservation, currentStatus, reservation.getStatus(), reason, actorId);
    }

    private void assertCurrentStatus(ReservationStatus currentStatus, ReservationStatus expectedStatus) {
        if (currentStatus != expectedStatus) {
            throw new AuthException("Reservation must be " + expectedStatus + " before this transition", HttpStatus.BAD_REQUEST);
        }
    }

    private void applyReservationRequest(Reservation reservation, ReservationRequest request, UUID actorId, boolean creating) {
        reservation.setReservationCode(resolveReservationCode(
                reservation.getRestaurant().getId(),
                request.getReservationCode(),
                creating ? null : reservation.getReservationCode()
        ));
        reservation.setSource(request.getSource() == null ? ReservationSource.INTERNAL : request.getSource());
        reservation.setPartySize(request.getPartySize());
        reservation.setReservationStart(request.getReservationStart());
        reservation.setReservationEnd(request.getReservationEnd());
        reservation.setContactName(firstNonBlank(request.getContactName(), customerDisplayName(reservation.getCustomer())));
        reservation.setContactPhone(firstNonBlank(request.getContactPhone(), reservation.getCustomer() == null ? null : reservation.getCustomer().getPhone()));
        reservation.setContactEmail(firstNonBlank(request.getContactEmail(), reservation.getCustomer() == null ? null : reservation.getCustomer().getEmail()));
        reservation.setSeatingPreference(request.getSeatingPreference());
        reservation.setSpecialRequests(request.getSpecialRequests());
        reservation.setInternalNotes(request.getInternalNotes());
        reservation.setUpdatedBy(actorId);
        applyDepositFields(reservation, request.getDepositRequired(), request.getDepositAmount(), creating);
        validateReservationWindow(request.getReservationStart(), request.getReservationEnd());
    }

    private void applyReservationPatch(Reservation reservation, UpdateReservationRequest request, UUID actorId) {
        if (request.getReservationCode() != null) {
            reservation.setReservationCode(resolveReservationCode(
                    reservation.getRestaurant().getId(),
                    request.getReservationCode(),
                    reservation.getReservationCode()
            ));
        }
        if (request.getSource() != null) {
            reservation.setSource(request.getSource());
        }
        if (request.getPartySize() != null) {
            reservation.setPartySize(request.getPartySize());
        }
        if (request.getReservationStart() != null) {
            reservation.setReservationStart(request.getReservationStart());
        }
        if (request.getReservationEnd() != null) {
            reservation.setReservationEnd(request.getReservationEnd());
        }
        if (request.getContactName() != null) {
            reservation.setContactName(request.getContactName());
        }
        if (request.getContactPhone() != null) {
            reservation.setContactPhone(request.getContactPhone());
        }
        if (request.getContactEmail() != null) {
            reservation.setContactEmail(request.getContactEmail());
        }
        if (request.getSeatingPreference() != null) {
            reservation.setSeatingPreference(request.getSeatingPreference());
        }
        if (request.getSpecialRequests() != null) {
            reservation.setSpecialRequests(request.getSpecialRequests());
        }
        if (request.getInternalNotes() != null) {
            reservation.setInternalNotes(request.getInternalNotes());
        }
        if (request.getDepositRequired() != null || request.getDepositAmount() != null) {
            applyDepositFields(
                    reservation,
                    request.getDepositRequired() == null ? reservation.isDepositRequired() : request.getDepositRequired(),
                    request.getDepositAmount() == null ? reservation.getDepositAmount() : request.getDepositAmount(),
                    false
            );
        }
        reservation.setUpdatedBy(actorId);
        validateReservationWindow(reservation.getReservationStart(), reservation.getReservationEnd());
    }

    private void applyPublicReservationRequest(Reservation reservation, PublicReservationRequest request) {
        reservation.setReservationCode(generateReservationCode(reservation.getRestaurant().getId()));
        reservation.setPartySize(request.getPartySize());
        reservation.setReservationStart(request.getReservationStart());
        reservation.setReservationEnd(request.getReservationEnd());
        reservation.setContactName(request.getContactName());
        reservation.setContactPhone(request.getContactPhone());
        reservation.setContactEmail(request.getContactEmail());
        reservation.setSeatingPreference(request.getSeatingPreference());
        reservation.setSpecialRequests(request.getSpecialRequests());
        applyDepositFields(reservation, request.getDepositRequired(), request.getDepositAmount(), true);
        validateReservationWindow(request.getReservationStart(), request.getReservationEnd());
    }

    private void applyDepositFields(
            Reservation reservation,
            Boolean depositRequiredValue,
            BigDecimal depositAmount,
            boolean creating
    ) {
        boolean depositRequired = Boolean.TRUE.equals(depositRequiredValue);
        reservation.setDepositRequired(depositRequired);
        reservation.setDepositAmount(depositRequired ? depositAmount : null);
        if (!depositRequired) {
            reservation.setDepositStatus(ReservationDepositStatus.NOT_REQUIRED);
            return;
        }

        if (creating || reservation.getDepositStatus() == null || reservation.getDepositStatus() == ReservationDepositStatus.NOT_REQUIRED) {
            reservation.setDepositStatus(ReservationDepositStatus.PENDING);
            return;
        }

        reservation.setDepositStatus(normalizeDepositStatusForRequiredReservation(reservation.getDepositStatus()));
    }

    private ReservationDepositStatus normalizeDepositStatusForRequiredReservation(ReservationDepositStatus currentStatus) {
        if (currentStatus == null || currentStatus == ReservationDepositStatus.NOT_REQUIRED) {
            return ReservationDepositStatus.PENDING;
        }
        return currentStatus;
    }

    private void replaceReservationTables(Reservation reservation, Collection<UUID> tableIds, UUID primaryTableId, UUID actorId) {
        if (tableIds == null) {
            return;
        }

        if (tableIds.isEmpty()) {
            new ArrayList<>(reservation.getTableAssignments()).forEach(reservation::removeTableAssignment);
            return;
        }

        SelectionValidationResult validation = validateTableSelection(
                reservation.getBranch(),
                reservation.getId(),
                reservation.getReservationStart(),
                reservation.getReservationEnd(),
                reservation.getPartySize(),
                tableIds,
                primaryTableId
        );

        new ArrayList<>(reservation.getTableAssignments()).forEach(reservation::removeTableAssignment);

        for (UUID tableId : validation.selectedTableIds()) {
            ReservationTableAssignment assignment = new ReservationTableAssignment();
            assignment.setRestaurantTable(validation.selectedTables().get(tableId));
            assignment.setPrimaryAssignment(Objects.equals(tableId, validation.primaryTableId()));
            assignment.setAssignedBy(actorId);
            reservation.addTableAssignment(assignment);
        }
    }

    private SelectionValidationResult validateTableSelection(
            Branch branch,
            UUID reservationId,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            int partySize,
            Collection<UUID> rawTableIds,
            UUID primaryTableId
    ) {
        validateReservationWindow(reservationStart, reservationEnd);

        LinkedHashSet<UUID> uniqueTableIds = new LinkedHashSet<>(rawTableIds);
        if (uniqueTableIds.size() != rawTableIds.size()) {
            throw new AuthException("tableIds must not contain duplicates", HttpStatus.BAD_REQUEST);
        }

        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(
                branch.getRestaurant().getId(),
                branch.getId()
        );
        Map<UUID, RestaurantTable> tablesById = snapshot.tablesById();
        Map<UUID, RestaurantTable> selectedTables = new LinkedHashMap<>();
        for (UUID tableId : uniqueTableIds) {
            RestaurantTable table = tablesById.get(tableId);
            if (table == null) {
                throw new AuthException("tableIds must only reference tables in this branch", HttpStatus.BAD_REQUEST);
            }
            if (table.getMergedInto() != null) {
                throw new AuthException("Merged child tables cannot be assigned directly", HttpStatus.BAD_REQUEST);
            }
            if (!table.isActive()) {
                throw new AuthException("Inactive tables cannot be assigned", HttpStatus.BAD_REQUEST);
            }
            if (table.getStatus() != TableStatus.AVAILABLE) {
                throw new AuthException("Only AVAILABLE tables can be assigned", HttpStatus.BAD_REQUEST);
            }
            selectedTables.put(tableId, table);
        }

        UUID resolvedPrimaryTableId = primaryTableId == null ? uniqueTableIds.iterator().next() : primaryTableId;
        if (!selectedTables.containsKey(resolvedPrimaryTableId)) {
            throw new AuthException("primaryTableId must reference one of the selected tableIds", HttpStatus.BAD_REQUEST);
        }

        Set<UUID> unavailableTableIds = overlappingAssignedTableIds(
                branch.getId(),
                reservationStart,
                reservationEnd,
                reservationId
        );
        for (RestaurantTable table : selectedTables.values()) {
            if (unavailableTableIds.contains(table.getId())) {
                throw new AuthException("Selected tables overlap with another reservation in the requested window", HttpStatus.CONFLICT);
            }
            for (RestaurantTable child : snapshot.childrenByParentId().getOrDefault(table.getId(), List.of())) {
                if (unavailableTableIds.contains(child.getId())) {
                    throw new AuthException("Selected tables overlap with another reservation in the requested window", HttpStatus.CONFLICT);
                }
            }
        }

        int effectiveCapacity = selectedTables.values().stream()
                .mapToInt(table -> restaurantTableSupport.effectiveCapacity(
                        table,
                        snapshot.childrenByParentId().getOrDefault(table.getId(), List.of())
                ))
                .sum();
        if (effectiveCapacity < partySize) {
            throw new AuthException("Selected tables do not provide enough capacity for this reservation", HttpStatus.BAD_REQUEST);
        }

        return new SelectionValidationResult(List.copyOf(uniqueTableIds), selectedTables, resolvedPrimaryTableId);
    }

    private Set<UUID> overlappingAssignedTableIds(
            UUID branchId,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            UUID currentReservationId
    ) {
        return reservationRepository.findAllByBranch_IdAndStatusInAndReservationStartLessThanAndReservationEndGreaterThanOrderByReservationStartAsc(
                        branchId,
                        BLOCKING_STATUSES,
                        reservationEnd,
                        reservationStart
                ).stream()
                .filter(reservation -> !Objects.equals(reservation.getId(), currentReservationId))
                .flatMap(reservation -> reservation.getTableAssignments().stream())
                .map(assignment -> assignment.getRestaurantTable().getId())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private List<ReservationAvailabilityOptionResponse> availabilityOptionsForBranch(
            Branch branch,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            int partySize,
            int limit
    ) {
        validateReservationWindow(reservationStart, reservationEnd);
        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(
                branch.getRestaurant().getId(),
                branch.getId()
        );
        List<AvailableRootTable> availableRootTables = loadAvailableRootTables(
                branch,
                snapshot,
                reservationStart,
                reservationEnd,
                null
        );
        return buildAvailabilityOptions(availableRootTables, partySize, limit);
    }

    private List<AvailableRootTable> loadAvailableRootTables(
            Branch branch,
            RestaurantTableSupport.BranchTableSnapshot snapshot,
            OffsetDateTime reservationStart,
            OffsetDateTime reservationEnd,
            UUID currentReservationId
    ) {
        Set<UUID> unavailableTableIds = overlappingAssignedTableIds(
                branch.getId(),
                reservationStart,
                reservationEnd,
                currentReservationId
        );

        return snapshot.tables().stream()
                .filter(table -> table.getMergedInto() == null)
                .filter(RestaurantTable::isActive)
                .filter(table -> table.getStatus() == TableStatus.AVAILABLE)
                .map(table -> {
                    List<RestaurantTable> children = snapshot.childrenByParentId().getOrDefault(table.getId(), List.of());
                    boolean blocked = unavailableTableIds.contains(table.getId())
                            || children.stream().anyMatch(child -> unavailableTableIds.contains(child.getId()));
                    if (blocked) {
                        return null;
                    }
                    return new AvailableRootTable(
                            table,
                            restaurantTableSupport.effectiveCapacity(table, children)
                    );
                })
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(AvailableRootTable::effectiveCapacity)
                        .thenComparing(table -> table.table().getTableNumber(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ReservationAvailabilityOptionResponse> buildAvailabilityOptions(
            List<AvailableRootTable> availableRootTables,
            int partySize,
            int limit
    ) {
        List<TableCombination> combinations = new ArrayList<>();
        buildAvailabilityOptionsDepthFirst(
                availableRootTables,
                partySize,
                limit,
                0,
                new ArrayList<>(),
                0,
                combinations,
                new HashSet<>()
        );

        return combinations.stream()
                .sorted(Comparator
                        .comparingInt(TableCombination::tableCount)
                        .thenComparingInt(combination -> combination.totalCapacity() - partySize)
                        .thenComparing(TableCombination::tableNumbersKey, String.CASE_INSENSITIVE_ORDER))
                .limit(limit)
                .map(combination -> ReservationAvailabilityOptionResponse.builder()
                        .tableIds(combination.tableIds())
                        .tableNumbers(combination.tableNumbers())
                        .primaryTableId(combination.tableIds().get(0))
                        .tableCount(combination.tableCount())
                        .totalCapacity(combination.totalCapacity())
                        .exactFit(combination.totalCapacity() == partySize)
                        .build())
                .toList();
    }

    private void buildAvailabilityOptionsDepthFirst(
            List<AvailableRootTable> availableRootTables,
            int partySize,
            int limit,
            int startIndex,
            List<AvailableRootTable> currentSelection,
            int currentCapacity,
            List<TableCombination> combinations,
            Set<String> seenKeys
    ) {
        if (currentCapacity >= partySize) {
            List<UUID> tableIds = currentSelection.stream().map(selection -> selection.table().getId()).toList();
            List<String> tableNumbers = currentSelection.stream().map(selection -> selection.table().getTableNumber()).toList();
            String key = tableIds.stream().map(UUID::toString).collect(Collectors.joining("|"));
            if (seenKeys.add(key)) {
                combinations.add(new TableCombination(
                        tableIds,
                        tableNumbers,
                        currentSelection.size(),
                        currentCapacity,
                        String.join("|", tableNumbers)
                ));
            }
            return;
        }

        if (currentSelection.size() >= MAX_COMBINATION_DEPTH || combinations.size() >= limit * 4) {
            return;
        }

        for (int index = startIndex; index < availableRootTables.size(); index++) {
            currentSelection.add(availableRootTables.get(index));
            buildAvailabilityOptionsDepthFirst(
                    availableRootTables,
                    partySize,
                    limit,
                    index + 1,
                    currentSelection,
                    currentCapacity + availableRootTables.get(index).effectiveCapacity(),
                    combinations,
                    seenKeys
            );
            currentSelection.remove(currentSelection.size() - 1);
        }
    }

    private List<Reservation> loadBranchReservations(UUID branchId, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return reservationRepository.findAllByBranch_IdOrderByReservationStartAsc(branchId);
        }
        requireCompleteWindow(from, to);
        return reservationRepository.findAllByBranch_IdAndReservationStartBetweenOrderByReservationStartAsc(branchId, from, to);
    }

    private Reservation requireReservation(UUID restaurantId, UUID reservationId) {
        return reservationRepository.findByIdAndRestaurant_Id(reservationId, restaurantId)
                .orElseThrow(ReservationNotFoundException::new);
    }

    private Reservation requirePublicReservation(String reservationCode) {
        String normalizedCode = NormalizationUtils.normalizeCode(reservationCode, 50);
        if (normalizedCode == null) {
            throw new AuthException("Reservation not found", HttpStatus.NOT_FOUND);
        }

        Reservation reservation = reservationRepository.findTopByReservationCodeOrderByCreatedAtDesc(normalizedCode)
                .orElseThrow(() -> new AuthException("Reservation not found", HttpStatus.NOT_FOUND));
        assertPublicAvailability(reservation.getRestaurant(), reservation.getBranch());
        return reservation;
    }

    private Customer resolveCustomer(UUID restaurantId, UUID customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(customerId, restaurantId)
                .orElseThrow(CustomerNotFoundException::new);
    }

    private Branch resolveManagedBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        if (branchId == null) {
            throw new AuthException("branchId is required", HttpStatus.BAD_REQUEST);
        }
        return restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
    }

    private Branch resolveManagedBranch(Authentication authentication, UUID restaurantId, UUID branchId, UUID fallbackBranchId) {
        return resolveManagedBranch(authentication, restaurantId, branchId == null ? fallbackBranchId : branchId);
    }

    private Branch requirePublicBranch(String restaurantSlug, String branchCode) {
        String normalizedRestaurantSlug = NormalizationUtils.normalizeLower(restaurantSlug);
        String normalizedBranchCode = NormalizationUtils.normalizeCode(branchCode, 100);
        if (normalizedRestaurantSlug == null || normalizedBranchCode == null) {
            throw new AuthException("Branch not available for public booking", HttpStatus.NOT_FOUND);
        }

        Branch branch = branchRepository.findByRestaurant_SlugAndCodeAndDeletedAtIsNull(normalizedRestaurantSlug, normalizedBranchCode)
                .orElseThrow(() -> new AuthException("Branch not available for public booking", HttpStatus.NOT_FOUND));
        assertPublicAvailability(branch.getRestaurant(), branch);
        return branch;
    }

    private void assertPublicAvailability(Restaurant restaurant, Branch branch) {
        if (restaurant == null || branch == null || !restaurant.isActive() || !branch.isActive()) {
            throw new AuthException("Branch not available for public booking", HttpStatus.NOT_FOUND);
        }
    }

    private String resolveReservationCode(UUID restaurantId, String requestedCode, String existingCode) {
        String normalizedCode = NormalizationUtils.normalizeCode(requestedCode, 50);
        if (normalizedCode == null) {
            return existingCode == null ? generateReservationCode(restaurantId) : existingCode;
        }
        if (!normalizedCode.equals(existingCode) && reservationRepository.existsByRestaurant_IdAndReservationCode(restaurantId, normalizedCode)) {
            throw new AuthException("Reservation code already exists in this restaurant", HttpStatus.CONFLICT);
        }
        return normalizedCode;
    }

    private String generateReservationCode(UUID restaurantId) {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "RES_" + UuidCreator.getTimeOrdered().toString().replace("-", "").substring(0, 8).toUpperCase();
            if (!reservationRepository.existsByRestaurant_IdAndReservationCode(restaurantId, candidate)) {
                return candidate;
            }
        }
        throw new AuthException("Reservation code could not be generated", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Reservation saveReservation(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Reservation update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private ReservationNote saveReservationNote(ReservationNote note) {
        try {
            return reservationNoteRepository.saveAndFlush(note);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Reservation note violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return reservationMapper.toResponse(
                reservation,
                reservation.getTableAssignments().stream()
                        .sorted(Comparator.comparing(ReservationTableAssignment::isPrimaryAssignment).reversed()
                                .thenComparing(ReservationTableAssignment::getAssignedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList()
        );
    }

    private List<ReservationTableAssignmentResponse> mapAssignments(Reservation reservation) {
        return reservation.getTableAssignments().stream()
                .sorted(Comparator.comparing(ReservationTableAssignment::isPrimaryAssignment).reversed()
                        .thenComparing(ReservationTableAssignment::getAssignedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(reservationMapper::toTableAssignmentResponse)
                .toList();
    }

    private UUID currentPrimaryTableId(Reservation reservation) {
        return reservation.getTableAssignments().stream()
                .filter(ReservationTableAssignment::isPrimaryAssignment)
                .map(assignment -> assignment.getRestaurantTable().getId())
                .findFirst()
                .orElseGet(() -> reservation.getTableAssignments().isEmpty()
                        ? null
                        : reservation.getTableAssignments().get(0).getRestaurantTable().getId());
    }

    private void addStatusHistory(
            Reservation reservation,
            ReservationStatus oldStatus,
            ReservationStatus newStatus,
            String reason,
            UUID changedBy
    ) {
        ReservationStatusHistory history = new ReservationStatusHistory();
        history.setOldStatus(oldStatus);
        history.setNewStatus(newStatus);
        history.setReason(reason);
        history.setChangedBy(changedBy);
        reservation.addStatusHistory(history);
    }

    private void validateReservationWindow(OffsetDateTime reservationStart, OffsetDateTime reservationEnd) {
        requireCompleteWindow(reservationStart, reservationEnd);
        if (!reservationEnd.isAfter(reservationStart)) {
            throw new AuthException("reservationEnd must be after reservationStart", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireCompleteWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new AuthException("from and to must both be provided together", HttpStatus.BAD_REQUEST);
        }
    }

    private TimeWindow resolveSummaryWindow(Branch branch, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return dayWindow(branch, LocalDate.now(restaurantZone(branch.getRestaurant())));
        }
        requireCompleteWindow(from, to);
        return new TimeWindow(from, to);
    }

    private TimeWindow resolveCapacityWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return new TimeWindow(now, now.plusHours(2));
        }
        requireCompleteWindow(from, to);
        if (!to.isAfter(from)) {
            throw new AuthException("to must be after from", HttpStatus.BAD_REQUEST);
        }
        return new TimeWindow(from, to);
    }

    private TimeWindow dayWindow(Branch branch, LocalDate date) {
        ZoneId zoneId = restaurantZone(branch.getRestaurant());
        ZonedDateTime start = date.atStartOfDay(zoneId);
        return new TimeWindow(start.toOffsetDateTime(), start.plusDays(1).toOffsetDateTime());
    }

    private ZoneId restaurantZone(Restaurant restaurant) {
        try {
            return ZoneId.of(restaurant.getTimezone());
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }

    private int countByStatus(List<Reservation> reservations, ReservationStatus status) {
        return (int) reservations.stream().filter(reservation -> reservation.getStatus() == status).count();
    }

    private int resolveAvailabilityLimit(Integer requestedLimit, int defaultLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultLimit;
        }
        return Math.min(requestedLimit, 20);
    }

    private void requireDepositConfigured(Reservation reservation) {
        if (!reservation.isDepositRequired() || reservation.getDepositAmount() == null) {
            throw new AuthException("Reservation does not require a deposit", HttpStatus.BAD_REQUEST);
        }
    }

    private String customerDisplayName(Customer customer) {
        if (customer == null) {
            return null;
        }
        if (customer.getFirstName() == null && customer.getLastName() == null) {
            return null;
        }
        if (customer.getFirstName() == null) {
            return customer.getLastName();
        }
        if (customer.getLastName() == null) {
            return customer.getFirstName();
        }
        return customer.getFirstName() + " " + customer.getLastName();
    }

    private String firstNonBlank(String firstValue, String fallbackValue) {
        return NormalizationUtils.normalize(firstValue) == null ? fallbackValue : firstValue;
    }

    private record TimeWindow(OffsetDateTime from, OffsetDateTime to) {
    }

    private record SelectionValidationResult(
            List<UUID> selectedTableIds,
            Map<UUID, RestaurantTable> selectedTables,
            UUID primaryTableId
    ) {
    }

    private record AvailableRootTable(
            RestaurantTable table,
            int effectiveCapacity
    ) {
    }

    private record TableCombination(
            List<UUID> tableIds,
            List<String> tableNumbers,
            int tableCount,
            int totalCapacity,
            String tableNumbersKey
    ) {
    }
}
