package pos.pos.reservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.reservation.dto.WaitlistEntryRequest;
import pos.pos.reservation.dto.WaitlistEntryResponse;
import pos.pos.reservation.entity.Reservation;
import pos.pos.reservation.entity.WaitlistEntry;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.reservation.enums.WaitlistStatus;
import pos.pos.reservation.repository.ReservationRepository;
import pos.pos.reservation.repository.WaitlistEntryRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.enums.TableStatus;
import pos.pos.tables.repository.RestaurantTableRepository;
import pos.pos.tables.service.RestaurantTableSupport;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Walk-ins waiting at the door when every table is taken: staff add them, see how long they've waited and roughly
// when a table should free up, then seat them at a table (or remove them if they leave; never a no-show).
@Service
@RequiredArgsConstructor
public class WaitlistService {

    private final RestaurantScopeService restaurantScopeService;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final RestaurantTableSupport restaurantTableSupport;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationRuleResolver reservationRuleResolver;

    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> getWaiting(Authentication authentication, UUID restaurantId, UUID branchId) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        List<WaitlistEntry> entries = waitlistEntryRepository.findAllByBranchIdAndStatusOrderByCreatedAtAsc(branchId, WaitlistStatus.WAITING);
        if (entries.isEmpty()) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        FloorForecast forecast = forecast(branch, now);
        return entries.stream().map(entry -> toResponse(entry, now, forecast)).toList();
    }

    @Transactional
    public WaitlistEntryResponse add(Authentication authentication, UUID restaurantId, UUID branchId, WaitlistEntryRequest request) {
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        WaitlistEntry entry = new WaitlistEntry();
        entry.setRestaurantId(restaurantId);
        entry.setBranchId(branchId);
        entry.setGuestName(request.getGuestName());
        entry.setContactPhone(request.getContactPhone());
        entry.setPartySize(request.getPartySize());
        entry.setNote(request.getNote());
        entry.setCreatedBy(actorId);
        entry.setUpdatedBy(actorId);
        WaitlistEntry saved = waitlistEntryRepository.saveAndFlush(entry);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return toResponse(saved, now, forecast(branch, now));
    }

    // Seats the group at a free table; the table shows occupied with them.
    @Transactional
    public WaitlistEntryResponse seat(Authentication authentication, UUID restaurantId, UUID branchId, UUID entryId, UUID tableId) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        WaitlistEntry entry = requireWaiting(restaurantId, branchId, entryId);
        RestaurantTable table = restaurantTableRepository.findByIdAndBranchIdForUpdate(tableId, branchId)
                .orElseThrow(() -> new AuthException("Table not found", HttpStatus.NOT_FOUND));
        if (!table.isActive() || table.getMergedInto() != null) {
            throw new AuthException("Choose an active table that isn't joined to another", HttpStatus.BAD_REQUEST);
        }
        if (table.getStatus() != TableStatus.AVAILABLE && table.getStatus() != TableStatus.RESERVED) {
            throw new AuthException("Table " + table.getTableNumber() + " isn't free", HttpStatus.CONFLICT);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        table.setStatus(TableStatus.OCCUPIED);
        table.setGuestCount(entry.getPartySize());
        table.setSeatedAt(now);
        table.setUpdatedBy(actorId);
        restaurantTableRepository.save(table);
        entry.setStatus(WaitlistStatus.SEATED);
        entry.setTableId(tableId);
        entry.setSeatedAt(now);
        entry.setUpdatedBy(actorId);
        return toResponse(waitlistEntryRepository.saveAndFlush(entry), now, FloorForecast.NONE);
    }

    @Transactional
    public void remove(Authentication authentication, UUID restaurantId, UUID branchId, UUID entryId) {
        restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        WaitlistEntry entry = requireWaiting(restaurantId, branchId, entryId);
        entry.setStatus(WaitlistStatus.LEFT);
        entry.setLeftAt(OffsetDateTime.now(ZoneOffset.UTC));
        entry.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        waitlistEntryRepository.saveAndFlush(entry);
    }

    private WaitlistEntry requireWaiting(UUID restaurantId, UUID branchId, UUID entryId) {
        WaitlistEntry entry = waitlistEntryRepository.findByIdAndRestaurantIdAndBranchId(entryId, restaurantId, branchId)
                .orElseThrow(() -> new AuthException("Waitlist entry not found", HttpStatus.NOT_FOUND));
        if (entry.getStatus() != WaitlistStatus.WAITING) {
            throw new AuthException("This group isn't waiting anymore", HttpStatus.CONFLICT);
        }
        return entry;
    }

    private WaitlistEntryResponse toResponse(WaitlistEntry entry, OffsetDateTime now, FloorForecast forecast) {
        Estimate estimate = forecast.forGroup(entry.getPartySize());
        return WaitlistEntryResponse.builder()
                .id(entry.getId())
                .guestName(entry.getGuestName())
                .contactPhone(entry.getContactPhone())
                .partySize(entry.getPartySize())
                .note(entry.getNote())
                .status(entry.getStatus())
                .createdAt(entry.getCreatedAt())
                .waitedMinutes((int) Math.max(0, Duration.between(entry.getCreatedAt(), now).toMinutes()))
                .tableFreeNow(estimate.freeNow())
                .tableFreeAround(estimate.freeAround())
                .tableId(entry.getTableId())
                .seatedAt(entry.getSeatedAt())
                .build();
    }

    // Each table big enough: free now, or when its seated guests should leave (their booking's end, or the usual
    // booking length after they sat down for walk-ins).
    private FloorForecast forecast(Branch branch, OffsetDateTime now) {
        RestaurantTableSupport.BranchTableSnapshot snapshot = restaurantTableSupport.loadBranchTables(branch.getRestaurant().getId(), branch.getId());
        Map<UUID, OffsetDateTime> seatedUntil = new HashMap<>();
        for (Reservation seated : reservationRepository.findAllByBranch_IdAndStatusInAndReservationStartLessThanAndReservationEndGreaterThanOrderByReservationStartAsc(
                branch.getId(), EnumSet.of(ReservationStatus.SEATED), now.plusDays(1), now.minusDays(1))) {
            seated.getTableAssignments().forEach(assignment -> seatedUntil.merge(
                    assignment.getRestaurantTable().getId(), seated.getReservationEnd(), (a, b) -> a.isAfter(b) ? a : b));
        }
        Duration usualVisit = Duration.ofMinutes(reservationRuleResolver.durationMinutes(branch, now));
        List<TableOutlook> tables = snapshot.tables().stream()
                .filter(RestaurantTable::isActive)
                .filter(table -> table.getMergedInto() == null)
                .map(table -> {
                    int capacity = restaurantTableSupport.effectiveCapacity(table, snapshot.childrenByParentId().getOrDefault(table.getId(), List.of()));
                    if (table.getStatus() == TableStatus.AVAILABLE) {
                        return new TableOutlook(capacity, now);
                    }
                    if (table.getStatus() != TableStatus.OCCUPIED) {
                        return null;
                    }
                    OffsetDateTime until = seatedUntil.get(table.getId());
                    if (until == null && table.getSeatedAt() != null) {
                        until = table.getSeatedAt().plus(usualVisit);
                    }
                    return until == null ? null : new TableOutlook(capacity, until.isBefore(now) ? now : until);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new FloorForecast(tables, now);
    }

    private record TableOutlook(int capacity, OffsetDateTime freeAt) {
    }

    private record Estimate(boolean freeNow, OffsetDateTime freeAround) {
    }

    private record FloorForecast(List<TableOutlook> tables, OffsetDateTime now) {
        static final FloorForecast NONE = new FloorForecast(List.of(), null);

        Estimate forGroup(int guests) {
            OffsetDateTime first = tables.stream()
                    .filter(table -> table.capacity() >= guests)
                    .map(TableOutlook::freeAt)
                    .min(OffsetDateTime::compareTo)
                    .orElse(null);
            boolean freeNow = first != null && now != null && !first.isAfter(now);
            return new Estimate(freeNow, freeNow ? null : first);
        }
    }
}
