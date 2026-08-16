package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.inventory.InventoryCountNotFoundException;
import pos.pos.exception.inventory.InventoryCountStateException;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.exception.inventory.InventoryLocationNotFoundException;
import pos.pos.inventory.dto.InventoryCountCreateRequest;
import pos.pos.inventory.dto.InventoryCountLineUpsertRequest;
import pos.pos.inventory.dto.InventoryCountResponse;
import pos.pos.inventory.entity.InventoryCount;
import pos.pos.inventory.entity.InventoryCountLine;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.enums.InventoryCountStatus;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.mapper.InventoryCountMapper;
import pos.pos.inventory.repository.InventoryCountRepository;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// A physical inventory count follows these steps:
// DRAFT -> IN_PROGRESS -> COMPLETED -> APPROVED
// It can also be CANCELLED before it is approved.
//
// Count lines can only be added, changed, or removed while the count is DRAFT or IN_PROGRESS.
// After completion, the lines are locked. An approved count cannot be changed.
//
// When a count is approved, differences between expected and counted quantities create
// COUNT_ADJUSTMENT movements. These movements update the current stock to match the physical count.
// Each inventory level is also marked with the time it was physically counted.
@Service
@RequiredArgsConstructor
public class InventoryCountService {

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryCountRepository inventoryCountRepository;
    private final InventoryLocationRepository inventoryLocationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryLevelService inventoryLevelService;
    private final InventoryMovementService inventoryMovementService;
    private final InventoryCountMapper inventoryCountMapper;

    //This method is only used to create a count and take the startinformation about the person, time and location it is created for
    //There is no other management action included
    @Transactional
    public InventoryCountResponse createCount(
            Authentication authentication,
            UUID restaurantId,
            InventoryCountCreateRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);

        InventoryLocation location = requireLocation(restaurantId, request.getLocationId());
        Branch branch = resolveBranch(restaurantId, request.getBranchId());

        //Creates a new Inventory Count and sets the given Information for the given count
        //A count is just gonna be created and is in Status Draft
        InventoryCount count = new InventoryCount();
        count.setRestaurant(restaurant);
        count.setBranch(branch);
        count.setLocation(location);
        count.setCountNumber(resolveCountNumber(request.getCountNumber()));
        count.setScheduledAt(request.getScheduledAt());
        count.setNotes(request.getNotes());
        count.setCreatedBy(actorId);
        count.setUpdatedBy(actorId);

        return inventoryCountMapper.toResponse(saveCount(count));
    }


    //Returns a specific Count by ID
    @Transactional(readOnly = true)
    public InventoryCountResponse getCount(Authentication authentication, UUID restaurantId, UUID countId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return inventoryCountMapper.toResponse(requireCount(restaurantId, countId));
    }


    //Gets a given optional Status and returns all counts on that status, if status not given returns all counts
    @Transactional(readOnly = true)
    public List<InventoryCountResponse> listCounts(Authentication authentication, UUID restaurantId, InventoryCountStatus status) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        List<InventoryCount> counts = status != null
                ? inventoryCountRepository.findAllByRestaurant_IdAndStatusOrderByCreatedAtDesc(restaurantId, status)
                : inventoryCountRepository.findAllByRestaurant_IdOrderByCreatedAtDesc(restaurantId);

        return counts.stream()
                .map(inventoryCountMapper::toResponse)
                .toList();
    }


    //A count is started and the status of the created count changes to IN_Progress and the updater is saved
    @Transactional
    public InventoryCountResponse startCount(Authentication authentication, UUID restaurantId, UUID countId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryCount count = requireCount(restaurantId, countId);

        if (count.getStatus() != InventoryCountStatus.DRAFT) {
            throw new InventoryCountStateException("Count must be DRAFT to start (currently " + count.getStatus() + ")");
        }

        count.setStatus(InventoryCountStatus.IN_PROGRESS);
        count.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        return inventoryCountMapper.toResponse(saveCount(count));
    }



    //Update or Insert a new count line on a count, if a count line(For a specific product) inside this COUNT alreasy exists just updates it
    //After that Only give the expected and actual value
    @Transactional
    public InventoryCountResponse upsertLine(
            Authentication authentication,
            UUID restaurantId,
            UUID countId,
            UUID itemId,
            InventoryCountLineUpsertRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        //Find the count and check if it is allowed to edit or no(DRAFT, IN_PROGRESS)
        InventoryCount count = requireCount(restaurantId, countId);
        assertLinesEditable(count);

        //Checks the id in the body and the URL for alidation
        if (request.getInventoryItemId() != null && !Objects.equals(request.getInventoryItemId(), itemId)) {
            throw new AuthException("inventoryItemId in the request body does not match the item in the URL", HttpStatus.BAD_REQUEST);
        }


        InventoryItem item = requireItem(restaurantId, itemId);

        //Checks if A line of count for this Item has already started on this count,
        // if so it gets the value counted and just needs to update it
        InventoryCountLine line = count.getLines().stream()
                .filter(existing -> existing.getInventoryItem() != null
                        && Objects.equals(existing.getInventoryItem().getId(), itemId))
                .findFirst()
                .orElseGet(() -> {
                    InventoryCountLine created = new InventoryCountLine();
                    created.setInventoryItem(item);
                    count.addLine(created);
                    return created;
                });

        // unit has no auto-fill in InventoryCountLine.normalizeFields() (unlike unitCostSnapshot,
        // which does), so it's set explicitly here on every upsert -- both creation and update --
        // instead of only at creation time. That way it always reflects the item's current
        // baseUnit, even if the item's unit changes after the line already existed.
        line.setUnit(item.getBaseUnit());

        //If no given queantity expectation, take the value from level
        BigDecimal expectedQuantity = request.getExpectedQuantity() != null
                ? request.getExpectedQuantity()
                : inventoryLevelService.currentOnHandQuantity(count.getLocation().getId(), itemId);

        line.setExpectedQuantity(expectedQuantity);
        line.setCountedQuantity(request.getCountedQuantity());
        line.setNotes(request.getNotes());

        return inventoryCountMapper.toResponse(saveCount(count));
    }


    //Based on the given countID and LineID finds the given Line on the specific Count, check if it is Editable
    // and and removes the line through the function on the Entity
    @Transactional
    public InventoryCountResponse removeLine(Authentication authentication, UUID restaurantId, UUID countId, UUID lineId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryCount count = requireCount(restaurantId, countId);
        assertLinesEditable(count);

        InventoryCountLine line = count.getLines().stream()
                .filter(existing -> Objects.equals(existing.getId(), lineId))
                .findFirst()
                .orElseThrow(() -> new AuthException("Count line not found", HttpStatus.NOT_FOUND));

        count.removeLine(line);

        return inventoryCountMapper.toResponse(saveCount(count));
    }


    //Checks the status to be in Progress to then complete it, calculates the varianceValue(Difference) from the expected value
    // and the exact one, and returns the Count.
    @Transactional
    public InventoryCountResponse completeCount(Authentication authentication, UUID restaurantId, UUID countId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryCount count = requireCount(restaurantId, countId);

        if (count.getStatus() != InventoryCountStatus.IN_PROGRESS) {
            throw new InventoryCountStateException("Count must be IN_PROGRESS to complete (currently " + count.getStatus() + ")");
        }

        BigDecimal totalVariance = count.getLines().stream()
                .map(InventoryCountLine::getVarianceValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        count.setStatus(InventoryCountStatus.COMPLETED);
        count.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        count.setVarianceValue(totalVariance);
        count.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        return inventoryCountMapper.toResponse(saveCount(count));
    }

    // Approving a count updates the real stock.
    // If the counted quantity differs from the expected quantity,
    // a COUNT_ADJUSTMENT movement corrects the inventory level.
    // Every counted item is also marked as physically checked.
    ///Note: If an Item goes inactive or untrackable after the count starts, count can not be approved
    /// becouse it is checked on applyMovement Method
    @Transactional
    public InventoryCountResponse approveCount(Authentication authentication, UUID restaurantId, UUID countId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryCount count = requireCount(restaurantId, countId);

        if (count.getStatus() != InventoryCountStatus.COMPLETED) {
            throw new InventoryCountStateException("Count must be COMPLETED to approve (currently " + count.getStatus() + ")");
        }


        UUID actorId = restaurantScopeService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        InventoryLocation location = count.getLocation();


        //For each line in this Specific Inventory Count takes the item that it is being counted
        //If the quantity of change not equal null or 0 then create a moovement
        // that applies the changes to that Specific item on the sysze,
        for (InventoryCountLine line : count.getLines()) {
            InventoryItem item = line.getInventoryItem();

            if (line.getVarianceQuantity() != null && line.getVarianceQuantity().signum() != 0) {
                inventoryMovementService.applyMovement(
                        restaurantId,
                        location,
                        item,
                        line.getVarianceQuantity(),
                        InventoryMovementType.COUNT_ADJUSTMENT,
                        line.getUnitCostSnapshot(),
                        "Physical count adjustment (" + count.getCountNumber() + ")",
                        "INVENTORY_COUNT",
                        count.getId(),
                        now,
                        null,
                        actorId
                );
            }

            inventoryLevelService.markCounted(location, item, now);
        }

        count.setStatus(InventoryCountStatus.APPROVED);
        count.setApprovedByUser(restaurantScopeService.currentActor(authentication));
        count.setApprovedAt(now);
        count.setUpdatedBy(actorId);

        return inventoryCountMapper.toResponse(saveCount(count));
    }


    //Checks te Status of the count, if not approved sets it to Cancelled and finishes it
    @Transactional
    public InventoryCountResponse cancelCount(Authentication authentication, UUID restaurantId, UUID countId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        InventoryCount count = requireCount(restaurantId, countId);

        if (count.getStatus() == InventoryCountStatus.APPROVED) {
            throw new InventoryCountStateException("An approved count cannot be cancelled");
        }

        count.setStatus(InventoryCountStatus.CANCELLED);
        count.setUpdatedBy(restaurantScopeService.currentUserId(authentication));

        return inventoryCountMapper.toResponse(saveCount(count));
    }

    //Checks if the Lines are editable based on their STATUS
    private void assertLinesEditable(InventoryCount count) {
        if (count.getStatus() != InventoryCountStatus.DRAFT && count.getStatus() != InventoryCountStatus.IN_PROGRESS) {
            throw new InventoryCountStateException(
                    "Count lines can only be changed while the count is DRAFT or IN_PROGRESS (currently " + count.getStatus() + ")"
            );
        }
    }

    //Is a number that is used for the inventory which is not the ID but for the communication between workers
    //It is usually created based on time but if it is givve blank it will be created automatically
    private String resolveCountNumber(String rawCountNumber) {
        if (rawCountNumber != null && !rawCountNumber.isBlank()) {
            return rawCountNumber;
        }

        return "IC-" + System.currentTimeMillis();
    }

    //Returns count from DB
    private InventoryCount requireCount(UUID restaurantId, UUID countId) {
        return inventoryCountRepository.findByIdAndRestaurant_Id(countId, restaurantId)
                .orElseThrow(InventoryCountNotFoundException::new);
    }

    //Returns Location from DB
    private InventoryLocation requireLocation(UUID restaurantId, UUID locationId) {
        return inventoryLocationRepository.findByIdAndRestaurant_Id(locationId, restaurantId)
                .orElseThrow(InventoryLocationNotFoundException::new);
    }

    //Returns Item from DB
    private InventoryItem requireItem(UUID restaurantId, UUID itemId) {
        return inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId)
                .orElseThrow(InventoryItemNotFoundException::new);
    }

    //Returns Branch from DB
    private Branch resolveBranch(UUID restaurantId, UUID branchId) {
        if (branchId == null) {
            return null;
        }

        return restaurantScopeService.requireExistingBranch(restaurantId, branchId);
    }

    //Saves the Count to the Database immediately so no error happens on the try box
    private InventoryCount saveCount(InventoryCount count) {
        try {
            return inventoryCountRepository.saveAndFlush(count);
        } catch (DataIntegrityViolationException ex) {
            throw new AuthException("Inventory count update violates a data constraint", HttpStatus.BAD_REQUEST);
        } catch (IllegalStateException ex) {
            throw new AuthException(ex.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }
}
