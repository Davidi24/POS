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

public interface OrderRepository extends JpaRepository<Order, UUID> {

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

    // Join only one bag (lineItems). Discounts and options load separately in batches.
    // Adding either collection here would cause MultipleBagFetchException again.
    // lineItems.location is a ManyToOne (not a bag) so it is safe to join-fetch here; it must
    // be initialized in the outer transaction so the REQUIRES_NEW inventory calls can access it.
    @EntityGraph(attributePaths = {
            "branch",
            "customer",
            "restaurantTable",
            "reservation",
            "lineItems",
            "lineItems.menuItem",
            "lineItems.variant",
            "lineItems.location"
    })
    Optional<Order> findByIdAndRestaurant_Id(UUID orderId, UUID restaurantId);

    @EntityGraph(attributePaths = {
            "branch",
            "customer",
            "restaurantTable",
            "reservation",
            "lineItems",
            "lineItems.menuItem",
            "lineItems.variant",
            "lineItems.location"
    })
    Optional<Order> findByRestaurant_IdAndOrderNumber(UUID restaurantId, String orderNumber);

    boolean existsByRestaurant_IdAndOrderNumber(UUID restaurantId, String orderNumber);

    @EntityGraph(attributePaths = {"customer", "restaurantTable", "lineItems"})
    Optional<Order> findTopByRestaurantTable_IdAndStatusInOrderByOpenedAtDesc(UUID tableId, Collection<OrderStatus> statuses);

    @EntityGraph(attributePaths = {
            "branch",
            "customer",
            "restaurantTable",
            "lineItems",
            "lineItems.menuItem",
            "lineItems.variant",
            "lineItems.location"
    })
    Optional<Order> findTopByOrderNumberOrderByCreatedAtDesc(String orderNumber);
}
