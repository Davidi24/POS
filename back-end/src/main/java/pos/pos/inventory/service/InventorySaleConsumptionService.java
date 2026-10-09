package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.inventory.entity.InventorySalesSource;
import pos.pos.inventory.enums.InventoryMovementType;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.inventory.repository.InventoryMovementRepository;
import pos.pos.inventory.repository.InventorySalesSourceRepository;
import pos.pos.inventory.service.InventoryUnitConversion;
import pos.pos.order.entity.Order;
import pos.pos.order.entity.OrderLineItem;
import pos.pos.order.entity.OrderItemOption;
import pos.pos.recipe.entity.Recipe;
import pos.pos.recipe.entity.RecipeComponent;
import pos.pos.recipe.enums.RecipeComponentType;
import pos.pos.recipe.enums.RecipeStatus;
import pos.pos.recipe.repository.RecipeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Consumes the active recipe's tracked stock exactly once when an order line is fulfilled. */
@Service
@RequiredArgsConstructor
public class InventorySaleConsumptionService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int QUANTITY_SCALE = 9;

    private final RecipeRepository recipeRepository;
    private final InventorySalesSourceRepository sourceRepository;
    private final InventoryMovementRepository movementRepository;
    private final InventoryMovementService movementService;

    @Transactional
    public void consumeFulfilledLine(Order order, OrderLineItem lineItem, UUID actorId) {
        if (movementRepository.existsByOrderLineItem_IdAndMovementType(lineItem.getId(), InventoryMovementType.SALE_CONSUMPTION)) {
            return;
        }
        if (order.getBranch() == null) {
            throw new AuthException("A branch is required to consume sale inventory", HttpStatus.CONFLICT);
        }

        Recipe recipe = recipeRepository.findByRestaurant_IdAndMenuItem_IdAndStatus(
                order.getRestaurant().getId(), lineItem.getMenuItem().getId(), RecipeStatus.ACTIVE
        ).orElse(null);
        Map<UUID, RequiredItem> requiredItems = new HashMap<>();
        if (recipe != null) {
            accumulate(recipe, BigDecimal.valueOf(lineItem.getQuantity()), requiredItems, new HashSet<>());
        }
        for (OrderItemOption option : lineItem.getOptions()) {
            Recipe modifierRecipe = option.getInventoryRecipeSnapshot();
            if (modifierRecipe == null) {
                continue;
            }
            if (modifierRecipe.getStatus() != RecipeStatus.ACTIVE) {
                throw new AuthException("A selected modifier's inventory recipe is no longer active", HttpStatus.CONFLICT);
            }
            BigDecimal selectedQuantity = option.getInventoryRecipeQuantitySnapshot()
                    .multiply(BigDecimal.valueOf(option.getQuantity()));
            if (lineItem.isOptionsPerUnit()) {
                selectedQuantity = selectedQuantity.multiply(BigDecimal.valueOf(lineItem.getQuantity()));
            }
            BigDecimal multiplier = selectedQuantity.divide(
                    modifierRecipe.getYieldQuantity(), QUANTITY_SCALE, RoundingMode.HALF_UP
            );
            accumulate(modifierRecipe, multiplier, requiredItems, new HashSet<>());
        }
        if (requiredItems.isEmpty()) {
            return;
        }

        // Resolve and validate every source before creating the first movement. Any later stock conflict
        // still rolls the whole transaction back, including the order-line status change.
        List<Consumption> consumptions = new ArrayList<>();
        for (RequiredItem required : requiredItems.values().stream()
                .sorted(Comparator.comparing(value -> value.item().getId())).toList()) {
            InventorySalesSource source = sourceRepository.findByBranch_IdAndInventoryItem_Id(
                            order.getBranch().getId(), required.item().getId())
                    .orElseThrow(() -> new AuthException(
                            "Configure a sale stock location for " + required.item().getName() + " at this branch",
                            HttpStatus.CONFLICT
                    ));
            InventoryLocation location = source.getLocation();
            if (!location.isActive() || (location.getBranch() != null
                    && !order.getBranch().getId().equals(location.getBranch().getId()))) {
                throw new AuthException("The configured sale stock location is no longer valid", HttpStatus.CONFLICT);
            }
            consumptions.add(new Consumption(location, required.item(), required.quantity()));
        }

        for (Consumption consumption : consumptions) {
            movementService.consumeForSale(lineItem, consumption.location(), consumption.item(), consumption.quantity(), actorId);
        }
    }

    private void accumulate(
            Recipe recipe,
            BigDecimal multiplier,
            Map<UUID, RequiredItem> requiredItems,
            Set<UUID> activePath
    ) {
        if (!activePath.add(recipe.getId())) {
            throw new AuthException("A recipe cycle prevents sale inventory calculation", HttpStatus.CONFLICT);
        }
        for (RecipeComponent component : recipe.getComponents()) {
            // Optional ingredients are not consumed unless they become an explicit order modifier.
            if (component.isOptionalComponent()) {
                continue;
            }
            BigDecimal quantity = yieldAdjusted(component.getQuantity(), component.getYieldLossPercent()).multiply(multiplier);
            if (component.getComponentType() == RecipeComponentType.INVENTORY_ITEM) {
                InventoryItem item = component.getInventoryItem();
                if (!item.isTrackInventory()) {
                    continue;
                }
                if (!item.isActive()) {
                    throw new AuthException("A recipe ingredient is inactive: " + item.getName(), HttpStatus.CONFLICT);
                }
                BigDecimal baseQuantity = InventoryUnitConversion.convert(quantity, component.getUnit(), item.getBaseUnit());
                requiredItems.compute(item.getId(), (id, current) -> current == null
                        ? new RequiredItem(item, baseQuantity)
                        : new RequiredItem(item, current.quantity().add(baseQuantity)));
            } else {
                Recipe child = component.getChildRecipe();
                BigDecimal childYield = InventoryUnitConversion.convert(quantity, component.getUnit(), child.getYieldUnit());
                BigDecimal childMultiplier = childYield.divide(child.getYieldQuantity(), QUANTITY_SCALE, RoundingMode.HALF_UP);
                accumulate(child, childMultiplier, requiredItems, activePath);
            }
        }
        activePath.remove(recipe.getId());
    }

    private BigDecimal yieldAdjusted(BigDecimal quantity, BigDecimal lossPercent) {
        BigDecimal retained = BigDecimal.ONE.subtract((lossPercent == null ? BigDecimal.ZERO : lossPercent)
                .divide(HUNDRED, 9, RoundingMode.HALF_UP));
        if (retained.signum() <= 0) {
            throw new AuthException("A recipe ingredient has an invalid yield loss", HttpStatus.CONFLICT);
        }
        return quantity.divide(retained, QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    private record RequiredItem(InventoryItem item, BigDecimal quantity) {
    }

    private record Consumption(InventoryLocation location, InventoryItem item, BigDecimal quantity) {
    }
}
