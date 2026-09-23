package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.entity.InventoryMovement;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.InventoryMovementRepository;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.repository.OrderLineItemRepository;
import pos.pos.recipe.dto.RecipeExpansionLineResponse;
import pos.pos.recipe.dto.RecipeExpansionResponse;
import pos.pos.recipe.service.RecipeService;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

// Bridges Order line item lifecycle events to Inventory, mirroring KdsOrderSyncService's role in
// this codebase: a plain @Service the Order services call into explicitly at each lifecycle
// point (not an event listener, not logic embedded in OrderSupport). Lives in
// pos.pos.inventory.service, not pos.pos.order.service, specifically so it can call
// InventoryMovementService.applyMovement(...) and InventoryLevelService's reservation methods
// directly -- those stay package-private on purpose, and this keeps them that way instead of
// having to widen their visibility for one caller.
//
// IMPORTANT: every write method here uses REQUIRES_NEW, not the default (REQUIRED) propagation.
// Inventory bookkeeping is meant to be best-effort -- a missing recipe or a deactivated item
// must never block or roll back a real order action. Simply catching the exception in the
// calling Order method would NOT be enough on its own: once a RuntimeException propagates out of
// any @Transactional-proxied method sharing the same physical transaction, Spring marks that
// whole transaction rollback-only, and the OUTER order method would still fail with
// UnexpectedRollbackException on commit even though the exception was "handled" by a catch
// block further up the call stack. REQUIRES_NEW runs the inventory work in its own isolated
// transaction, so a failure here rolls back only the inventory side (atomically, satisfying the
// "a partial failure on ingredient 3 of 5 must not leave inventory half-updated" requirement)
// and never touches the Order transaction at all. Callers are expected to wrap each call in a
// try/catch and log a warning -- see OrderItemService/OrderWorkflowService's private try*
// helpers for the actual call sites; that's also why these methods don't catch their own
// exceptions internally, they let them propagate so REQUIRES_NEW's rollback behaves correctly.
@Service
@RequiredArgsConstructor
public class OrderInventoryIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(OrderInventoryIntegrationService.class);

    private final RecipeService recipeService;
    private final InventoryMovementService inventoryMovementService;
    private final InventoryLevelService inventoryLevelService;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final OrderLineItemRepository orderLineItemRepository;
    private final RestaurantScopeService restaurantScopeService;

    // Called when a line item is created (order opened / item added). Expands the recipe and
    // reserves (increments committedQuantity for) every raw ingredient needed. No-op if the
    // line item has no location chosen yet -- nothing to reserve against.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reserveForLineItem(Authentication authentication, OrderLineItem lineItem) {
        InventoryLocation location = lineItem.getLocation();
        if (location == null || lineItem.getMenuItem() == null) {
            return;
        }

        UUID restaurantId = lineItem.getOrder().getRestaurant().getId();
        RecipeExpansionResponse expansion = recipeService.expandToInventoryConsumption(
                authentication, restaurantId, lineItem.getMenuItem().getId(), BigDecimal.valueOf(lineItem.getQuantity())
        );

        for (RecipeExpansionLineResponse line : expansion.getLines()) {
            InventoryItem item = requireItem(restaurantId, line.getInventoryItemId());
            inventoryLevelService.reserveQuantity(location, item, line.getQuantity());
        }
    }

    // Called when a line item's status becomes FULFILLED. Idempotent: if SALE_CONSUMPTION
    // movements already exist for this line item, it's already been fulfilled from an inventory
    // perspective (a retry, or it was reached through more than one of the order module's
    // status-change paths) -- skip silently rather than deducting twice.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deductForFulfillment(Authentication authentication, OrderLineItem lineItem) {
        log.info("[inventory] deductForFulfillment called for lineItem={}", lineItem.getId());
        InventoryLocation location = lineItem.getLocation();
        if (location == null || lineItem.getMenuItem() == null || lineItem.getId() == null) {
            log.info("[inventory] deductForFulfillment early-return: location={} menuItem={} id={}", location, lineItem.getMenuItem() == null ? "null" : lineItem.getMenuItem().getId(), lineItem.getId());
            return;
        }

        boolean alreadyFulfilled = inventoryMovementRepository
                .findAllByOrderLineItem_IdOrderByOccurredAtAsc(lineItem.getId())
                .stream()
                .anyMatch(movement -> movement.getMovementType() == InventoryMovementType.SALE_CONSUMPTION);
        if (alreadyFulfilled) {
            return;
        }

        UUID restaurantId = lineItem.getOrder().getRestaurant().getId();
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        RecipeExpansionResponse expansion = recipeService.expandToInventoryConsumption(
                authentication, restaurantId, lineItem.getMenuItem().getId(), BigDecimal.valueOf(lineItem.getQuantity())
        );

        // getReferenceById creates a proxy managed by this (REQUIRES_NEW) session so that
        // InventoryMovement.validateState()'s lazy chain on orderLineItem works without a
        // LazyInitializationException -- the detached lineItem from the outer tx cannot be
        // passed directly because its uninitialized proxies (e.g. menuItem.section) are bound
        // to the suspended outer session and throw when accessed here.
        OrderLineItem managedLineItem = orderLineItemRepository.getReferenceById(lineItem.getId());

        for (RecipeExpansionLineResponse line : expansion.getLines()) {
            InventoryItem item = requireItem(restaurantId, line.getInventoryItemId());

            inventoryMovementService.applyMovement(
                    restaurantId,
                    location,
                    item,
                    line.getQuantity().negate(),
                    InventoryMovementType.SALE_CONSUMPTION,
                    null,
                    "Order line item fulfilled",
                    "ORDER_LINE_ITEM",
                    lineItem.getId(),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    managedLineItem,
                    actorId
            );

            // The reservation from reserveForLineItem is being "used up" now, not just released.
            inventoryLevelService.releaseReservedQuantity(location, item, line.getQuantity());
        }
    }

    // Called when a line item is voided or cancelled. Branches on whether it was ever actually
    // fulfilled: if SALE_CONSUMPTION movements exist, reverse them with new VOID movements
    // (stock physically left the shelf and needs to come back); if not, it was only ever
    // reserved, so just release that reservation -- nothing was ever deducted from onHandQuantity.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseOrReverseForLineItem(Authentication authentication, OrderLineItem lineItem) {
        if (lineItem.getId() == null) {
            return;
        }

        List<InventoryMovement> existingConsumption = inventoryMovementRepository
                .findAllByOrderLineItem_IdOrderByOccurredAtAsc(lineItem.getId())
                .stream()
                .filter(movement -> movement.getMovementType() == InventoryMovementType.SALE_CONSUMPTION)
                .toList();

        if (!existingConsumption.isEmpty()) {
            reverseConsumption(authentication, lineItem, existingConsumption);
        } else {
            releaseReservation(authentication, lineItem);
        }
    }

    // Mirrors KdsOrderSyncService.assertNoTicketHistory/assertNoLineItemHistory. Order.lineItems
    // uses orphanRemoval = true, and both split and merge remove line items from their original
    // order (cloning them elsewhere with a new id) -- removed + orphaned means hard-deleted. If
    // any InventoryMovement already references that line item, the delete would fail with a raw
    // foreign key violation instead of a clean error, so this blocks the operation earlier
    // instead, the same way the existing KDS guard already does for ticket history.
    @Transactional(readOnly = true)
    public void assertNoInventoryHistory(Order order, String message) {
        if (order == null) {
            return;
        }

        boolean hasHistory = order.getLineItems().stream()
                .filter(lineItem -> lineItem.getId() != null)
                .anyMatch(lineItem -> !inventoryMovementRepository
                        .findAllByOrderLineItem_IdOrderByOccurredAtAsc(lineItem.getId())
                        .isEmpty());

        if (hasHistory) {
            throw new AuthException(message, HttpStatus.BAD_REQUEST);
        }
    }

    private void reverseConsumption(Authentication authentication, OrderLineItem lineItem, List<InventoryMovement> existingConsumption) {
        UUID restaurantId = lineItem.getOrder().getRestaurant().getId();
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        // Same session-management fix as deductForFulfillment: use a proxy managed by the
        // current REQUIRES_NEW session so validateState() can traverse lazy associations.
        OrderLineItem managedLineItem = orderLineItemRepository.getReferenceById(lineItem.getId());

        // Derived from the actual historical movements, not a fresh recipe expansion -- the
        // recipe could have changed since fulfillment, so "what was actually deducted" is the
        // only correct source of truth for what needs to go back.
        for (InventoryMovement original : existingConsumption) {
            inventoryMovementService.applyMovement(
                    restaurantId,
                    original.getLocation(),
                    original.getInventoryItem(),
                    original.getQuantityDelta().negate(),
                    InventoryMovementType.VOID,
                    original.getUnitCostSnapshot(),
                    "Reversing order line item consumption",
                    "INVENTORY_MOVEMENT",
                    original.getId(),
                    OffsetDateTime.now(ZoneOffset.UTC),
                    managedLineItem,
                    actorId
            );
        }
    }

    private void releaseReservation(Authentication authentication, OrderLineItem lineItem) {
        InventoryLocation location = lineItem.getLocation();
        if (location == null || lineItem.getMenuItem() == null) {
            return;
        }

        UUID restaurantId = lineItem.getOrder().getRestaurant().getId();
        RecipeExpansionResponse expansion = recipeService.expandToInventoryConsumption(
                authentication, restaurantId, lineItem.getMenuItem().getId(), BigDecimal.valueOf(lineItem.getQuantity())
        );

        for (RecipeExpansionLineResponse line : expansion.getLines()) {
            InventoryItem item = requireItem(restaurantId, line.getInventoryItemId());
            inventoryLevelService.releaseReservedQuantity(location, item, line.getQuantity());
        }
    }

    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }
}
