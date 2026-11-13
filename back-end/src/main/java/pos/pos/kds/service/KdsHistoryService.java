package pos.pos.kds.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.kds.dto.KdsHistoryResponse;
import pos.pos.kds.entity.KdsTicket;
import pos.pos.kds.enums.KdsTicketStatus;
import pos.pos.kds.mapper.KdsMapper;
import pos.pos.kds.repository.KdsTicketRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;
import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
public class KdsHistoryService {
    private final RestaurantScopeService scope;
    private final KdsSupport support;
    private final KdsTicketRepository repository;
    private final KdsMapper mapper;

    @Transactional(readOnly = true)
    public KdsHistoryResponse history(Authentication authentication, UUID restaurantId, UUID branchId,
            UUID stationId, UUID deviceId, OffsetDateTime from, OffsetDateTime to,
            KdsTicketStatus status, int page, int size) {
        scope.requireAccessibleBranch(authentication, restaurantId, branchId);
        if (page < 0 || size < 1 || size > 100) throw bad("Page must be non-negative and size between 1 and 100");
        if (from != null && to != null && !from.isBefore(to)) throw bad("from must be before to");
        if (status != null && status != KdsTicketStatus.COMPLETED && status != KdsTicketStatus.CANCELLED)
            throw bad("History status must be COMPLETED or CANCELLED");
        UUID selected = stationId;
        if (deviceId != null) {
            var station = support.requireStationForDevice(restaurantId, deviceId);
            if (!branchId.equals(station.getBranch().getId())) throw bad("Device does not belong to this branch");
            if (stationId != null && !stationId.equals(station.getId())) throw bad("Station does not match this device");
            selected = station.getId();
        } else if (stationId != null) {
            support.requireStationInBranch(branchId, stationId);
        }
        final UUID selectedStation = selected;
        Specification<KdsTicket> filter = (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.equal(root.get("restaurant").get("id"), restaurantId));
            predicates.add(cb.equal(root.get("branch").get("id"), branchId));
            predicates.add(status == null ? root.get("status").in(KdsTicketStatus.COMPLETED, KdsTicketStatus.CANCELLED) : cb.equal(root.get("status"), status));
            if (selectedStation != null) predicates.add(cb.equal(root.get("station").get("id"), selectedStation));
            var ended = cb.coalesce(root.<OffsetDateTime>get("completedAt"), root.<OffsetDateTime>get("updatedAt"));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(ended, from));
            if (to != null) predicates.add(cb.lessThan(ended, to));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        // Do not fetch-join the item collection in a paged query: that makes Hibernate page in memory.
        var result = repository.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id")));
        return new KdsHistoryResponse(mapper.mapTicketResponses(result.getContent()), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }
    private AuthException bad(String message) { return new AuthException(message, HttpStatus.BAD_REQUEST); }
}
