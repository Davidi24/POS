package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.exception.inventory.InventoryLocationNotFoundException;
import pos.pos.exception.inventory.InventoryMovementNotFoundException;
import pos.pos.inventory.dto.InventoryAdjustmentRequest;
import pos.pos.inventory.dto.InventoryMovementResponse;
import pos.pos.inventory.dto.InventoryReceiveRequest;
import pos.pos.inventory.dto.InventoryReturnRequest;
import pos.pos.inventory.dto.InventoryTransferRequest;
import pos.pos.inventory.dto.InventoryWasteRequest;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.entity.InventoryMovement;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.mapper.InventoryMovementMapper;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.inventory.repository.InventoryMovementRepository;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Records every operation that changes inventory, such as receiving, waste, transfers,
// supplier returns, manual adjustments, and physical count adjustments.
// Every operation creates a movement history record and updates the current stock level
// by the same quantity, keeping the inventory history and current balance consistent.
@Service
@RequiredArgsConstructor
public class InventoryMovementService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryLevelService inventoryLevelService;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryLocationRepository inventoryLocationRepository;
    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryMovementMapper inventoryMovementMapper;

    //For Inventory Movement there were created different Request and Response DTO based on the movement type
    //The recieve method takes the request and applies the changes to the given item and location using the applyMovement method
    @Transactional
    public InventoryMovementResponse receive(Authentication authentication, UUID restaurantId, InventoryReceiveRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        InventoryLocation location = requireLocation(restaurantId, request.getLocationId());
        InventoryItem item = requireItem(restaurantId, request.getInventoryItemId());

        return applyMovement(
                restaurantId,
                location,
                item,
                request.getQuantity(),
                InventoryMovementType.RECEIPT,
                request.getUnitCostOverride(),
                request.getReason(),
                request.getReferenceType(),
                request.getReferenceId(),
                request.getOccurredAt(),
                null,
                actorId
        );
    }


    //Same logic as recieve ut using diferent Request which is waste
    @Transactional
    public InventoryMovementResponse logWaste(Authentication authentication, UUID restaurantId, InventoryWasteRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        InventoryLocation location = requireLocation(restaurantId, request.getLocationId());
        InventoryItem item = requireItem(restaurantId, request.getInventoryItemId());

        return applyMovement(
                restaurantId,
                location,
                item,
                request.getQuantity().negate(),
                InventoryMovementType.WASTE,
                null,
                request.getReason(),
                null,
                null,
                request.getOccurredAt(),
                null,
                actorId
        );
    }


    //Take the request for transfering different stocks and and return a list of the changes
    @Transactional
    public List<InventoryMovementResponse> transfer(Authentication authentication, UUID restaurantId, InventoryTransferRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        if (Objects.equals(request.getFromLocationId(), request.getToLocationId())) {
            throw new AuthException("fromLocationId and toLocationId must be different", HttpStatus.BAD_REQUEST);
        }


        //Gets the item and the locations for the transfer
        InventoryLocation fromLocation = requireLocation(restaurantId, request.getFromLocationId());
        InventoryLocation toLocation = requireLocation(restaurantId, request.getToLocationId());
        InventoryItem item = requireItem(restaurantId, request.getInventoryItemId());

        InventoryMovementResponse transferOut = applyMovement(
                restaurantId,
                fromLocation,
                item,
                request.getQuantity().negate(),
                InventoryMovementType.TRANSFER_OUT,
                null,
                request.getReason(),
                null,
                null,
                request.getOccurredAt(),
                null,
                actorId
        );

        InventoryMovementResponse transferIn = applyMovement(
                restaurantId,
                toLocation,
                item,
                request.getQuantity(),
                InventoryMovementType.TRANSFER_IN,
                null,
                request.getReason(),
                null,
                null,
                request.getOccurredAt(),
                null,
                actorId
        );

        return List.of(transferOut, transferIn);
    }


    //Used to return Items that were brought wrongly on our system, bad Movements, mistkes from suppliers...
    //Takes the request and applies the changes
    @Transactional
    public InventoryMovementResponse logReturn(Authentication authentication, UUID restaurantId, InventoryReturnRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        InventoryLocation location = requireLocation(restaurantId, request.getLocationId());
        InventoryItem item = requireItem(restaurantId, request.getInventoryItemId());

        return applyMovement(
                restaurantId,
                location,
                item,
                request.getQuantity().negate(),
                InventoryMovementType.RETURN,
                null,
                request.getReason(),
                null,
                null,
                request.getOccurredAt(),
                null,
                actorId
        );
    }


    //This method is used fot the adjustment of the changes when fr example a manual count is done or anything happened
    @Transactional
    public InventoryMovementResponse adjust(Authentication authentication, UUID restaurantId, InventoryAdjustmentRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        if (request.getQuantityDelta() == null || request.getQuantityDelta().signum() == 0) {
            throw new AuthException("quantityDelta must not be zero", HttpStatus.BAD_REQUEST);
        }

        InventoryLocation location = requireLocation(restaurantId, request.getLocationId());
        InventoryItem item = requireItem(restaurantId, request.getInventoryItemId());

        return applyMovement(
                restaurantId,
                location,
                item,
                request.getQuantityDelta(),
                InventoryMovementType.MANUAL_ADJUSTMENT,
                null,
                request.getReason(),
                null,
                null,
                request.getOccurredAt(),
                null,
                actorId
        );
    }

    //Returns the given Movement
    @Transactional(readOnly = true)
    public InventoryMovementResponse getMovement(Authentication authentication, UUID restaurantId, UUID movementId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        return inventoryMovementMapper.toResponse(
                inventoryMovementRepository.findByIdAndInventoryItem_Restaurant_Id(movementId, restaurantId)
                        .orElseThrow(InventoryMovementNotFoundException::new)
        );
    }

    // Single entry point for GET /inventory/movements. orderLineItemId, movementType, and itemId
    // are all optional and narrow the result together when more than one is supplied. Branching
    // here (instead of separate params-conditioned controller methods) is what avoids the
    // ambiguous mapping that used to blow up with a 500 when more than one query param was
    // supplied at once.
    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> listMovements(
            Authentication authentication,
            UUID restaurantId,
            UUID orderLineItemId,
            InventoryMovementType movementType,
            UUID itemId
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        if (itemId != null) {
            requireItem(restaurantId, itemId);
        }

        return inventoryMovementRepository.search(restaurantId, orderLineItemId, movementType, itemId).stream()
                .map(inventoryMovementMapper::toResponse)
                .toList();
    }

    // Every stock operation uses this method.
// It creates the movement history record and updates the current inventory level
// with the same quantity change, keeping the stock balance and history consistent.
// Package-private on purpose: InventoryCountService (same package) reuses this directly
// when a count gets approved, instead of duplicating movement-creation logic.
    InventoryMovementResponse applyMovement(
            UUID restaurantId,
            InventoryLocation location,
            InventoryItem item,
            BigDecimal quantityDelta,
            InventoryMovementType movementType,
            BigDecimal unitCostOverride,
            String reason,
            String referenceType,
            UUID referenceId,
            OffsetDateTime occurredAt,
            OrderLineItem orderLineItem,
            UUID actorId
    ) {
        assertItemTrackable(item);
        assertItemActive(item);

        BigDecimal unitCost = unitCostOverride != null ? unitCostOverride : item.getCostPerUnit();
        OffsetDateTime effectiveOccurredAt = occurredAt != null ? occurredAt : OffsetDateTime.now(ZoneOffset.UTC);

        InventoryMovement movement = new InventoryMovement();
        movement.setLocation(location);
        movement.setInventoryItem(item);
        movement.setOrderLineItem(orderLineItem);
        movement.setMovementType(movementType);
        movement.setQuantityDelta(quantityDelta);
        movement.setUnit(item.getBaseUnit());
        movement.setUnitCostSnapshot(unitCost);
        movement.setTotalCostDelta(quantityDelta.multiply(unitCost));
        movement.setReason(reason);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setOccurredAt(effectiveOccurredAt);
        movement.setCreatedBy(actorId);
        movement.setUpdatedBy(actorId);


        //Makes the changes and saves them as a new movement, after that also makes sure that the level Class is Updated
        InventoryMovement saved = saveMovement(movement);

        inventoryLevelService.upsertLevel(location, item, quantityDelta);

        return inventoryMovementMapper.toResponse(saved);
    }

    // Both checks live here, not in requireItem(), on purpose: requireItem() is also used just to
    // resolve/validate an item for reads, where inactive/non-tracked items still need to be
    // readable. These two only need to block the moment stock actually tries to move.
    private void assertItemTrackable(InventoryItem item) {
        if (!item.isTrackInventory()) {
            throw new AuthException(
                    "\"" + item.getName() + "\" does not track inventory, so stock movements are not allowed for it",
                    HttpStatus.CONFLICT
            );
        }
    }

    private void assertItemActive(InventoryItem item) {
        if (!item.isActive()) {
            throw new AuthException(
                    "\"" + item.getName() + "\" is deactivated, so stock movements are not allowed for it",
                    HttpStatus.CONFLICT
            );
        }
    }

    private InventoryLocation requireLocation(UUID restaurantId, UUID locationId) {
        return inventoryLocationRepository.findByIdAndRestaurant_Id(locationId, restaurantId)
                .orElseThrow(InventoryLocationNotFoundException::new);
    }

    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }


    //Noormal Saving method used to save each new Movement exactly at the given step on the database
    private InventoryMovement saveMovement(InventoryMovement movement) {
        try {
            return inventoryMovementRepository.saveAndFlush(movement);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Inventory movement violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
