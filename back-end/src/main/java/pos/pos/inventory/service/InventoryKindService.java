package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.inventory.dto.InventoryKindRequest;
import pos.pos.inventory.dto.InventoryKindResponse;
import pos.pos.inventory.entity.InventoryKind;
import pos.pos.inventory.enums.InventoryItemType;
import pos.pos.inventory.enums.InventoryKindScope;
import pos.pos.inventory.enums.InventoryLocationType;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.InventoryKindRepository;
import pos.pos.inventory.repository.InventoryLocationRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The kinds of stock items and of places (Inventory → Items → Kinds, and a place's Kind). The built-in kinds are there
 * from the start; the restaurant can remove any of them and add its own (user, 2026-10-30). What a removed kind held
 * goes to Uncategorized, which is always there and can't be removed.
 */
@Service
@RequiredArgsConstructor
public class InventoryKindService {

    private static final String UNCATEGORIZED = "UNCATEGORIZED";
    private static final String CUSTOM = "CUSTOM";

    private final RestaurantScopeService restaurantScopeService;
    private final InventoryKindRepository inventoryKindRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryLocationRepository inventoryLocationRepository;
    private final InventoryChangeNotifier inventoryChangeNotifier;

    @Transactional(readOnly = true)
    public List<InventoryKindResponse> listKinds(Authentication authentication, UUID restaurantId, InventoryKindScope scope) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return kinds(restaurantId, scope);
    }

    @Transactional
    public InventoryKindResponse createKind(Authentication authentication, UUID restaurantId, InventoryKindRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        String name = NormalizationUtils.normalize(request.getName());
        if (name == null || name.isBlank()) {
            throw new AuthException("Give the kind a name", HttpStatus.BAD_REQUEST);
        }
        if (inventoryKindRepository.existsByRestaurant_IdAndAppliesToAndBuiltinCodeIsNullAndDeletedAtIsNullAndNameIgnoreCase(
                restaurantId, request.getAppliesTo(), name)) {
            throw new AuthException("There's already a kind called " + name, HttpStatus.CONFLICT);
        }
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        InventoryKind kind = new InventoryKind();
        kind.setRestaurant(restaurant);
        kind.setAppliesTo(request.getAppliesTo());
        kind.setName(name);
        kind.setCreatedBy(actorId);
        kind.setUpdatedBy(actorId);
        InventoryKind saved = inventoryKindRepository.saveAndFlush(kind);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return InventoryKindResponse.builder().code(CUSTOM).id(saved.getId()).name(saved.getName()).builtIn(false).count(0).build();
    }

    /** Removes a kind: [ref] is a built-in code (INGREDIENT, KITCHEN …) or the id of one of the restaurant's own kinds. */
    @Transactional
    public void deleteKind(Authentication authentication, UUID restaurantId, InventoryKindScope scope, String ref) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (UNCATEGORIZED.equals(ref) || CUSTOM.equals(ref)) {
            throw new AuthException("Uncategorized stays: it's where things go when their kind is removed", HttpStatus.BAD_REQUEST);
        }
        if (builtInCodes(scope).contains(ref)) {
            if (scope == InventoryKindScope.ITEM) {
                inventoryItemRepository.uncategorizeType(restaurantId, InventoryItemType.valueOf(ref));
            } else {
                inventoryLocationRepository.uncategorizeType(restaurantId, InventoryLocationType.valueOf(ref));
            }
            boolean marked = inventoryKindRepository.findAllByRestaurant_IdAndAppliesToAndBuiltinCodeIsNotNull(restaurantId, scope).stream()
                    .anyMatch(kind -> ref.equals(kind.getBuiltinCode()));
            if (!marked) {
                InventoryKind mark = new InventoryKind();
                mark.setRestaurant(restaurant);
                mark.setAppliesTo(scope);
                mark.setName(ref);
                mark.setBuiltinCode(ref);
                mark.setDeletedAt(now);
                mark.setCreatedBy(actorId);
                mark.setUpdatedBy(actorId);
                inventoryKindRepository.saveAndFlush(mark);
            }
        } else {
            UUID id;
            try {
                id = UUID.fromString(ref);
            } catch (IllegalArgumentException e) {
                throw new AuthException("There's no such kind", HttpStatus.NOT_FOUND);
            }
            InventoryKind kind = inventoryKindRepository
                    .findByIdAndRestaurant_IdAndAppliesToAndBuiltinCodeIsNullAndDeletedAtIsNull(id, restaurantId, scope)
                    .orElseThrow(() -> new AuthException("There's no such kind", HttpStatus.NOT_FOUND));
            if (scope == InventoryKindScope.ITEM) {
                inventoryItemRepository.uncategorizeKind(id);
            } else {
                inventoryLocationRepository.uncategorizeKind(id);
            }
            InventoryKind live = inventoryKindRepository.findById(kind.getId()).orElseThrow();
            live.setDeletedAt(now);
            live.setUpdatedBy(actorId);
            inventoryKindRepository.saveAndFlush(live);
        }
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
    }

    /** One of the restaurant's own kinds that is still there, for an item or place of type CUSTOM. */
    @Transactional(readOnly = true)
    public InventoryKind requireOwnKind(UUID restaurantId, InventoryKindScope scope, UUID kindId) {
        if (kindId == null) {
            throw new AuthException("Choose which of your kinds it is", HttpStatus.BAD_REQUEST);
        }
        return inventoryKindRepository.findByIdAndRestaurant_IdAndAppliesToAndBuiltinCodeIsNullAndDeletedAtIsNull(kindId, restaurantId, scope)
                .orElseThrow(() -> new AuthException("That kind was removed. Choose another one", HttpStatus.BAD_REQUEST));
    }

    // Built-in kinds (unless removed and empty), then the restaurant's own by name, then Uncategorized.
    private List<InventoryKindResponse> kinds(UUID restaurantId, InventoryKindScope scope) {
        Map<String, Long> counts = new HashMap<>();
        List<Object[]> rows = scope == InventoryKindScope.ITEM
                ? inventoryItemRepository.countsByKind(restaurantId)
                : inventoryLocationRepository.countsByKind(restaurantId);
        for (Object[] row : rows) {
            String type = String.valueOf(row[0]);
            String key = CUSTOM.equals(type) && row[1] != null ? row[1].toString() : type;
            counts.merge(key, ((Number) row[2]).longValue(), Long::sum);
        }
        Set<String> removed = inventoryKindRepository.findAllByRestaurant_IdAndAppliesToAndBuiltinCodeIsNotNull(restaurantId, scope).stream()
                .map(InventoryKind::getBuiltinCode)
                .collect(Collectors.toSet());
        List<InventoryKindResponse> result = new ArrayList<>();
        for (String code : builtInCodes(scope)) {
            long count = counts.getOrDefault(code, 0L);
            if (!removed.contains(code) || count > 0) {
                result.add(InventoryKindResponse.builder().code(code).builtIn(true).count(count).build());
            }
        }
        for (InventoryKind kind : inventoryKindRepository
                .findAllByRestaurant_IdAndAppliesToAndBuiltinCodeIsNullAndDeletedAtIsNullOrderByNameAsc(restaurantId, scope)) {
            result.add(InventoryKindResponse.builder().code(CUSTOM).id(kind.getId()).name(kind.getName()).builtIn(false)
                    .count(counts.getOrDefault(kind.getId().toString(), 0L)).build());
        }
        result.add(InventoryKindResponse.builder().code(UNCATEGORIZED).builtIn(true).count(counts.getOrDefault(UNCATEGORIZED, 0L)).build());
        return result;
    }

    // The built-in kinds in their usual order, without Uncategorized and Custom.
    private static List<String> builtInCodes(InventoryKindScope scope) {
        List<String> codes = new ArrayList<>();
        if (scope == InventoryKindScope.ITEM) {
            for (InventoryItemType type : InventoryItemType.values()) codes.add(type.name());
        } else {
            for (InventoryLocationType type : InventoryLocationType.values()) codes.add(type.name());
        }
        codes.remove(UNCATEGORIZED);
        codes.remove(CUSTOM);
        return codes;
    }
}
