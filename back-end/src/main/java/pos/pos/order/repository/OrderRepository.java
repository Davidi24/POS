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

    @EntityGraph(attributePaths = {"customer", "restaurantTable"})
    Optional<Order> findTopByRestaurantTable_IdAndStatusInOrderByOpenedAtDesc(UUID tableId, Collection<OrderStatus> statuses);

    // This top-row lookup is used by the public checkout flow. Avoid fetching lineItems here: Hibernate would
    // apply the max-results limit in memory. The caller loads the collection within its transaction when needed.
    @EntityGraph(attributePaths = {"branch", "customer", "restaurantTable"})
    Optional<Order> findTopByOrderNumberOrderByCreatedAtDesc(String orderNumber);

    // Whether a booking already has orders (or payments on them) at its table.
    boolean existsByReservation_IdAndStatusIn(UUID reservationId, Collection<OrderStatus> statuses);
}
