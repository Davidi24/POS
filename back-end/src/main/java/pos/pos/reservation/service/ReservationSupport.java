package pos.pos.reservation.service;

import com.github.f4b6a3.uuid.UuidCreator;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pos.pos.customer.entity.Customer;
import pos.pos.customer.repository.CustomerRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.customer.CustomerNotFoundException;
import pos.pos.exception.reservation.ReservationNotFoundException;
import pos.pos.reservation.dto.PublicReservationRequest;
import pos.pos.reservation.dto.PublicReservationResponse;
import pos.pos.reservation.dto.PublicTableLookupResponse;
import pos.pos.reservation.dto.ReservationAuditResponse;
import pos.pos.reservation.dto.ReservationDepositResponse;
import pos.pos.reservation.dto.ReservationNoteResponse;
import pos.pos.reservation.dto.ReservationRequest;
import pos.pos.reservation.dto.ReservationResponse;
import pos.pos.reservation.dto.ReservationStatusHistoryResponse;
import pos.pos.reservation.dto.ReservationTableAssignmentResponse;
import pos.pos.reservation.dto.UpdateReservationRequest;
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
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
@lombok.RequiredArgsConstructor
public class ReservationSupport {

    private final RestaurantScopeService restaurantScopeService;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationNoteRepository reservationNoteRepository;
    private final ReservationMapper reservationMapper;

    public Reservation requireReservation(UUID restaurantId, UUID reservationId) {
        return reservationRepository.findByIdAndRestaurant_Id(reservationId, restaurantId)
                .orElseThrow(ReservationNotFoundException::new);
    }

    public Reservation requirePublicReservation(String reservationCode) {
        String normalizedCode = NormalizationUtils.normalizeCode(reservationCode, 50);
        if (normalizedCode == null) {
            throw new AuthException("Reservation not found", HttpStatus.NOT_FOUND);
        }

        Reservation reservation = reservationRepository.findTopByReservationCodeOrderByCreatedAtDesc(normalizedCode)
                .orElseThrow(() -> new AuthException("Reservation not found", HttpStatus.NOT_FOUND));
        assertPublicAvailability(reservation.getRestaurant(), reservation.getBranch());
        return reservation;
    }

    public Customer resolveCustomer(UUID restaurantId, UUID customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(customerId, restaurantId)
                .orElseThrow(CustomerNotFoundException::new);
    }

    public Branch resolveManagedBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        if (branchId == null) {
            throw new AuthException("branchId is required", HttpStatus.BAD_REQUEST);
        }
        return restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
    }

    public Branch resolveManagedBranch(Authentication authentication, UUID restaurantId, UUID branchId, UUID fallbackBranchId) {
        return resolveManagedBranch(authentication, restaurantId, branchId == null ? fallbackBranchId : branchId);
    }

    public Branch requirePublicBranch(String restaurantSlug, String branchCode) {
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

    public void assertPublicAvailability(Restaurant restaurant, Branch branch) {
        if (restaurant == null || branch == null || !restaurant.isActive() || !branch.isActive()) {
            throw new AuthException("Branch not available for public booking", HttpStatus.NOT_FOUND);
        }
    }

    public Reservation saveReservation(Reservation reservation) {
        try {
            return reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Reservation update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public ReservationNote saveReservationNote(ReservationNote note) {
        try {
            return reservationNoteRepository.saveAndFlush(note);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Reservation note violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public ReservationResponse toResponse(Reservation reservation) {
        return reservationMapper.toResponse(
                reservation,
                reservation.getTableAssignments().stream()
                        .sorted(Comparator.comparing(ReservationTableAssignment::isPrimaryAssignment).reversed()
                                .thenComparing(ReservationTableAssignment::getAssignedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .toList()
        );
    }

    public List<ReservationTableAssignmentResponse> mapAssignments(Reservation reservation) {
        return reservation.getTableAssignments().stream()
                .sorted(Comparator.comparing(ReservationTableAssignment::isPrimaryAssignment).reversed()
                        .thenComparing(ReservationTableAssignment::getAssignedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(reservationMapper::toTableAssignmentResponse)
                .toList();
    }

    public ReservationTableAssignmentResponse toTableAssignmentResponse(ReservationTableAssignment assignment) {
        return reservationMapper.toTableAssignmentResponse(assignment);
    }

    public List<ReservationStatusHistoryResponse> mapStatusHistory(List<ReservationStatusHistory> history) {
        return history.stream().map(reservationMapper::toStatusHistoryResponse).toList();
    }

    public List<ReservationNoteResponse> mapNotes(List<ReservationNote> notes) {
        return notes.stream().map(reservationMapper::toNoteResponse).toList();
    }

    public ReservationNoteResponse toNoteResponse(ReservationNote note) {
        return reservationMapper.toNoteResponse(note);
    }

    public ReservationDepositResponse toDepositResponse(Reservation reservation) {
        return reservationMapper.toDepositResponse(reservation);
    }

    public ReservationAuditResponse toAuditResponse(
            Reservation reservation,
            List<ReservationStatusHistoryResponse> statusHistory,
            List<ReservationNoteResponse> notes,
            List<ReservationTableAssignmentResponse> tableAssignments
    ) {
        return reservationMapper.toAuditResponse(reservation, statusHistory, notes, tableAssignments);
    }

    public PublicReservationResponse toPublicResponse(Reservation reservation, List<ReservationTableAssignment> assignments) {
        return reservationMapper.toPublicResponse(reservation, assignments);
    }

    public PublicTableLookupResponse toPublicTableLookupResponse(RestaurantTable table, int effectiveCapacity) {
        return reservationMapper.toPublicTableLookupResponse(table, effectiveCapacity);
    }

    public UUID currentPrimaryTableId(Reservation reservation) {
        return reservation.getTableAssignments().stream()
                .filter(ReservationTableAssignment::isPrimaryAssignment)
                .map(assignment -> assignment.getRestaurantTable().getId())
                .findFirst()
                .orElseGet(() -> reservation.getTableAssignments().isEmpty()
                        ? null
                        : reservation.getTableAssignments().get(0).getRestaurantTable().getId());
    }

    public void addStatusHistory(
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

    public void validateReservationWindow(OffsetDateTime reservationStart, OffsetDateTime reservationEnd) {
        requireCompleteWindow(reservationStart, reservationEnd);
        if (!reservationEnd.isAfter(reservationStart)) {
            throw new AuthException("reservationEnd must be after reservationStart", HttpStatus.BAD_REQUEST);
        }
    }

    public void requireCompleteWindow(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            throw new AuthException("from and to must both be provided together", HttpStatus.BAD_REQUEST);
        }
    }

    public TimeWindow resolveSummaryWindow(Branch branch, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return dayWindow(branch, LocalDate.now(restaurantZone(branch.getRestaurant())));
        }
        requireCompleteWindow(from, to);
        return new TimeWindow(from, to);
    }

    public TimeWindow resolveCapacityWindow(OffsetDateTime from, OffsetDateTime to) {
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

    public TimeWindow dayWindow(Branch branch, LocalDate date) {
        ZoneId zoneId = restaurantZone(branch.getRestaurant());
        ZonedDateTime start = date.atStartOfDay(zoneId);
        return new TimeWindow(start.toOffsetDateTime(), start.plusDays(1).toOffsetDateTime());
    }

    public ZoneId restaurantZone(Restaurant restaurant) {
        try {
            return ZoneId.of(restaurant.getTimezone());
        } catch (Exception ex) {
            return ZoneOffset.UTC;
        }
    }

    public int countByStatus(List<Reservation> reservations, ReservationStatus status) {
        return (int) reservations.stream().filter(reservation -> reservation.getStatus() == status).count();
    }

    // It returns the requested limit, but maximum 20.
    public int resolveAvailabilityLimit(Integer requestedLimit, int defaultLimit) {
        if (requestedLimit == null || requestedLimit <= 0) {
            return defaultLimit;
        }
        return Math.min(requestedLimit, 20);
    }

    public void requireDepositConfigured(Reservation reservation) {
        if (!reservation.isDepositRequired() || reservation.getDepositAmount() == null) {
            throw new AuthException("Reservation does not require a deposit", HttpStatus.BAD_REQUEST);
        }
    }

    public void applyReservationRequest(Reservation reservation, ReservationRequest request, UUID actorId, boolean creating) {
        if (creating) {
            reservation.setReservationCode(generateReservationCode(reservation.getRestaurant().getId()));
        }
        reservation.setSource(request.getSource() == null ? ReservationSource.INTERNAL : request.getSource());
        reservation.setPartySize(request.getPartySize());
        reservation.setReservationStart(request.getReservationStart());
        reservation.setReservationEnd(request.getReservationEnd());
        reservation.setContactName(firstNonBlank(request.getContactName(), customerDisplayName(reservation.getCustomer())));
        reservation.setContactPhone(firstNonBlank(
                request.getContactPhone(),
                reservation.getCustomer() == null ? null : reservation.getCustomer().getPhone()
        ));
        reservation.setContactEmail(firstNonBlank(
                request.getContactEmail(),
                reservation.getCustomer() == null ? null : reservation.getCustomer().getEmail()
        ));
        reservation.setSeatingPreference(request.getSeatingPreference());
        reservation.setSpecialRequests(request.getSpecialRequests());
        reservation.setInternalNotes(request.getInternalNotes());
        reservation.setUpdatedBy(actorId);
        applyDepositFields(reservation, request.getDepositRequired(), request.getDepositAmount(), creating);
        validateReservationWindow(request.getReservationStart(), request.getReservationEnd());
    }

    public void applyReservationPatch(Reservation reservation, UpdateReservationRequest request, UUID actorId) {
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

    public void applyPublicReservationRequest(Reservation reservation, PublicReservationRequest request) {
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

    public void applyDepositFields(
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

    public ReservationDepositStatus normalizeDepositStatusForRequiredReservation(ReservationDepositStatus currentStatus) {
        if (currentStatus == null || currentStatus == ReservationDepositStatus.NOT_REQUIRED) {
            return ReservationDepositStatus.PENDING;
        }
        return currentStatus;
    }

    public String customerDisplayName(Customer customer) {
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

    public String firstNonBlank(String firstValue, String fallbackValue) {
        return NormalizationUtils.normalize(firstValue) == null ? fallbackValue : firstValue;
    }

    public record TimeWindow(OffsetDateTime from, OffsetDateTime to) {
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
}
