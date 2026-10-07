package pos.pos.order.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.order.dto.OrderHistoryPage;
import pos.pos.order.dto.OrderResponse;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderStatus;
import pos.pos.order.enums.OrderLineItemStatus;
import pos.pos.order.repository.OrderLineItemRepository;
import pos.pos.order.repository.OrderRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderHistoryService {
    private final RestaurantScopeService scope;
    private final OrderRepository repository;
    private final OrderLineItemRepository lineItemRepository;
    private final OrderSupport support;
    private static final Set<OrderStatus> TERMINAL = EnumSet.of(OrderStatus.CLOSED, OrderStatus.CANCELLED, OrderStatus.VOIDED);

    @Transactional(readOnly = true)
    public OrderHistoryPage history(Authentication authentication, UUID restaurantId, UUID branchId,
            OffsetDateTime from, OffsetDateTime to, OrderStatus status, UUID customerId, UUID staffId,
            String search, int page, int size) {
        if (status != null && !TERMINAL.contains(status)) throw bad("History status must be CLOSED, CANCELLED or VOIDED");
        return page(authentication, restaurantId, branchId, from, to, status, customerId, staffId, search, page, size, true, false);
    }

    @Transactional(readOnly = true)
    public OrderHistoryPage orders(Authentication authentication, UUID restaurantId, UUID branchId,
            OffsetDateTime from, OffsetDateTime to, OrderStatus status, UUID customerId,
            String search, int page, int size, boolean historyOnly, boolean openOnly) {
        if (historyOnly && openOnly) throw bad("historyOnly and openOnly cannot both be true");
        if (openOnly && status != null && status != OrderStatus.DRAFT && status != OrderStatus.OPEN)
            throw bad("Open orders status must be DRAFT or OPEN");
        return page(authentication, restaurantId, branchId, from, to, status, customerId, null, search, page, size, historyOnly, openOnly);
    }

    private OrderHistoryPage page(Authentication authentication, UUID restaurantId, UUID branchId,
            OffsetDateTime from, OffsetDateTime to, OrderStatus status, UUID customerId, UUID staffId,
            String search, int page, int size, boolean historyOnly, boolean openOnly) {
        scope.requireAccessibleBranch(authentication, restaurantId, branchId);
        if (page < 0 || size < 1 || size > 100) throw bad("Page must be non-negative and size between 1 and 100");
        if (from != null && to != null && !from.isBefore(to)) throw bad("from must be before to");
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        if (term.length() > 200) throw bad("Search must not exceed 200 characters");
        Specification<Order> filter = (r, q, cb) -> {
            var clauses = new ArrayList<Predicate>();
            clauses.add(cb.equal(r.get("restaurant").get("id"), restaurantId));
            clauses.add(cb.equal(r.get("branch").get("id"), branchId));
            if (historyOnly) clauses.add(status == null ? r.get("status").in(TERMINAL)
                    : TERMINAL.contains(status) ? cb.equal(r.get("status"), status) : cb.disjunction());
            else if (openOnly) clauses.add(status == null ? r.get("status").in(OrderStatus.DRAFT, OrderStatus.OPEN)
                    : cb.equal(r.get("status"), status));
            else if (status != null) clauses.add(cb.equal(r.get("status"), status));
            // History date means when the order opened, matching the existing order-history contract.
            if (from != null) clauses.add(cb.greaterThanOrEqualTo(r.get("openedAt"), from));
            if (to != null) clauses.add(cb.lessThan(r.get("openedAt"), to));
            if (customerId != null) clauses.add(cb.equal(r.get("customer").get("id"), customerId));
            if (staffId != null) clauses.add(cb.equal(r.get("createdBy"), staffId));
            if (!term.isBlank()) {
                String escaped = "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                var table = r.join("restaurantTable", jakarta.persistence.criteria.JoinType.LEFT);
                var customer = r.join("customer", jakarta.persistence.criteria.JoinType.LEFT);
                clauses.add(cb.or(cb.like(cb.lower(r.get("orderNumber")), escaped, '!'),
                        cb.like(cb.lower(r.get("notes")), escaped, '!'),
                        cb.like(cb.lower(table.get("tableNumber")), escaped, '!'),
                        cb.like(cb.lower(table.get("name")), escaped, '!'),
                        cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(customer.get("firstName"), ""), " "), cb.coalesce(customer.get("lastName"), ""))), escaped, '!')));
            }
            return cb.and(clauses.toArray(Predicate[]::new));
        };
        // No collection fetch join: the database, not Hibernate, limits the result.
        var result = repository.findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt", "id")));
        List<UUID> ids = result.getContent().stream().map(Order::getId).toList();
        List<OrderResponse> items;
        if (ids.isEmpty()) {
            items = List.of();
        } else {
            Map<UUID, Order> ordersById = new HashMap<>();
            repository.findSummaryGraphByIds(ids).forEach(order -> ordersById.put(order.getId(), order));
            Map<UUID, Integer> countsByOrderId = new HashMap<>();
            lineItemRepository.sumActiveQuantitiesByOrderIds(ids,
                    List.of(OrderLineItemStatus.CANCELLED, OrderLineItemStatus.VOIDED)).forEach(row ->
                    countsByOrderId.put((UUID) row[0], Math.toIntExact(((Number) row[1]).longValue())));
            items = ids.stream().map(id -> {
                Order order = ordersById.get(id);
                if (order == null) throw new IllegalStateException("A paged order disappeared while building its response");
                return support.toSummaryResponse(order, countsByOrderId.getOrDefault(id, 0));
            }).toList();
        }
        return new OrderHistoryPage(items,
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), result.hasNext());
    }
    private AuthException bad(String message) { return new AuthException(message, HttpStatus.BAD_REQUEST); }
}
