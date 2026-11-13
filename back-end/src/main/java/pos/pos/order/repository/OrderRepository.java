package pos.pos.order.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.order.entity.Order;
import pos.pos.order.enums.OrderStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Order> {
    @EntityGraph(attributePaths = {"restaurant", "branch", "restaurantTable", "reservation", "customer"})
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id in :ids")
    List<Order> findSummaryGraphByIds(@org.springframework.data.repository.query.Param("ids") Collection<UUID> ids);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Order o where o.id = :id and o.restaurant.id = :restaurantId")
    Optional<Order> findForUpdate(@org.springframework.data.repository.query.Param("id") UUID id,
        @org.springframework.data.repository.query.Param("restaurantId") UUID restaurantId);


    @EntityGraph(attributePaths = {"branch", "customer", "restaurantTable"})
    List<Order> findAllByRestaurant_IdOrderByOpenedAtDesc(UUID restaurantId);

    @EntityGraph(attributePaths = {"branch", "customer", "restaurantTable"})
    List<Order> findAllByRestaurant_IdAndOpenedAtBetweenOrderByOpenedAtDesc(
            UUID restaurantId,
            OffsetDateTime from,
            OffsetDateTime to
    );

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Order> findAllByBranch_IdAndStatusInOrderByOpenedAtDesc(UUID branchId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {"branch", "customer", "restaurantTable"})
    List<Order> findAllByRestaurantTable_IdInAndStatusInOrderByOpenedAtAsc(
            Collection<UUID> tableIds,
            Collection<OrderStatus> statuses
    );

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Order> findAllByBranch_IdAndOpenedAtBetweenOrderByOpenedAtDesc(
            UUID branchId,
            OffsetDateTime from,
            OffsetDateTime to
    );

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Order> findAllByBranch_IdAndStatusInAndOpenedAtBetweenOrderByOpenedAtDesc(
            UUID branchId,
            Collection<OrderStatus> statuses,
            OffsetDateTime from,
            OffsetDateTime to
    );

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    List<Order> findAllByBranch_IdOrderByOpenedAtDesc(UUID branchId);

    @EntityGraph(attributePaths = {"branch", "restaurantTable"})
    List<Order> findAllByCustomer_IdAndRestaurant_IdOrderByOpenedAtDesc(UUID customerId, UUID restaurantId);

    // Fetch one collection only; other collections load inside the service transaction.
    @EntityGraph(attributePaths = {
            "branch",
            "customer",
            "restaurantTable",
            "reservation",
            "lineItems",
            "lineItems.menuItem",
            "lineItems.variant"
    })
    Optional<Order> findByIdAndRestaurant_Id(UUID orderId, UUID restaurantId);

    // Fetch one collection only; other collections load inside the service transaction.
    @EntityGraph(attributePaths = {
            "branch",
            "customer",
            "restaurantTable",
            "reservation",
            "lineItems",
            "lineItems.menuItem",
            "lineItems.variant"
    })
    Optional<Order> findByRestaurant_IdAndOrderNumber(UUID restaurantId, String orderNumber);

    boolean existsByRestaurant_IdAndOrderNumber(UUID restaurantId, String orderNumber);

    // How much of each order has been served, by dish count, leaving out what was cancelled.
    @org.springframework.data.jpa.repository.Query("""
            select l.order.id as orderId,
                   sum(l.quantity) as total,
                   sum(case when l.status = pos.pos.order.enums.OrderLineItemStatus.FULFILLED then l.quantity else 0 end) as served
            from OrderLineItem l
            where l.order.id in :orderIds
              and l.status not in (pos.pos.order.enums.OrderLineItemStatus.CANCELLED, pos.pos.order.enums.OrderLineItemStatus.VOIDED)
            group by l.order.id
            """)
    List<OrderServedCount> countServed(Collection<UUID> orderIds);

    /** When each of these tables last had an order closed and fully paid: table id, closed at. */
    @org.springframework.data.jpa.repository.Query("""
            select o.restaurantTable.id, max(o.closedAt) from Order o
            where o.restaurantTable.id in :tableIds and o.status = pos.pos.order.enums.OrderStatus.CLOSED
              and o.paymentStatus = pos.pos.order.enums.OrderPaymentStatus.PAID
            group by o.restaurantTable.id
            """)
    List<Object[]> lastPaidCloseByTable(Collection<UUID> tableIds);

    /** When the dishes of each of these orders last changed: order id, time. */
    @org.springframework.data.jpa.repository.Query("""
            select l.order.id, max(l.updatedAt) from OrderLineItem l where l.order.id in :orderIds group by l.order.id
            """)
    List<Object[]> lastLineChange(Collection<UUID> orderIds);

    /** Orders reopened only to take their payment. */
    @org.springframework.data.jpa.repository.Query("""
            select o.id from Order o where o.status = pos.pos.order.enums.OrderStatus.OPEN and o.unpaidAt is not null
            """)
    List<UUID> findIdsOpenForPaymentOnly();

    interface OrderServedCount {
        UUID getOrderId();

        Long getTotal();

        Long getServed();
    }

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    Optional<Order> findTopByRestaurantTable_IdAndStatusInOrderByOpenedAtDesc(UUID tableId, Collection<OrderStatus> statuses);

    // This top-row lookup is used by the public checkout flow. Avoid fetching lineItems here: Hibernate would
    // apply the max-results limit in memory. The caller loads the collection within its transaction when needed.
    @EntityGraph(attributePaths = {"branch", "customer", "restaurantTable"})
    Optional<Order> findTopByOrderNumberOrderByCreatedAtDesc(String orderNumber);

    // Whether a booking already has orders (or payments on them) at its table.
    boolean existsByReservation_IdAndStatusIn(UUID reservationId, Collection<OrderStatus> statuses);
}
