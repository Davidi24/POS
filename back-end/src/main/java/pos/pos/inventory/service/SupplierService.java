package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.inventory.InventoryItemNotFoundException;
import pos.pos.inventory.dto.SupplierItemRequest;
import pos.pos.inventory.dto.SupplierPlaceRequest;
import pos.pos.inventory.dto.SupplierRequest;
import pos.pos.inventory.dto.SupplierResponse;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.Supplier;
import pos.pos.inventory.entity.SupplierItemLink;
import pos.pos.inventory.entity.SupplierPlace;
import pos.pos.inventory.repository.InventoryItemRepository;
import pos.pos.inventory.repository.SupplierItemLinkRepository;
import pos.pos.inventory.repository.SupplierPlaceRepository;
import pos.pos.inventory.repository.SupplierRepository;
import pos.pos.restaurant.service.RestaurantScopeService;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Inventory workspace → Suppliers, kept by the server (2026-10-30): suppliers, their places and the items you buy from
 * them. A stock item's supplier is picked from these ({@link #attachToItem}). Reading needs INVENTORY_READ, changes
 * INVENTORY_UPDATE (as the rest of Inventory).
 */
@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierPlaceRepository placeRepository;
    private final SupplierItemLinkRepository linkRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final RestaurantScopeService restaurantScopeService;
    private final InventoryChangeNotifier inventoryChangeNotifier;

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        List<Supplier> suppliers = supplierRepository.findAllByRestaurantIdAndDeletedAtIsNullOrderByNameAsc(restaurantId);
        Set<UUID> ids = suppliers.stream().map(Supplier::getId).collect(Collectors.toSet());
        Map<UUID, List<SupplierPlace>> places = ids.isEmpty() ? Map.of()
                : placeRepository.findAllBySupplierIdInOrderByCreatedAtAsc(ids).stream().collect(Collectors.groupingBy(SupplierPlace::getSupplierId));
        Map<UUID, List<SupplierItemLink>> links = ids.isEmpty() ? Map.of()
                : linkRepository.findAllBySupplierIdIn(ids).stream().collect(Collectors.groupingBy(SupplierItemLink::getSupplierId));
        return suppliers.stream()
                .map(s -> response(s, places.getOrDefault(s.getId(), List.of()), links.getOrDefault(s.getId(), List.of())))
                .toList();
    }

    @Transactional
    public SupplierResponse create(Authentication authentication, UUID restaurantId, SupplierRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        if (request.getId() != null && supplierRepository.existsById(request.getId())) {
            throw new AuthException("This supplier was already added", HttpStatus.CONFLICT);
        }
        assertNameFree(restaurantId, request.getName(), null);
        Supplier supplier = new Supplier();
        supplier.setId(request.getId());
        supplier.setRestaurantId(restaurantId);
        supplier.setCreatedBy(restaurantScopeService.currentUserId(authentication));
        apply(supplier, request);
        Supplier saved = supplierRepository.saveAndFlush(supplier);
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return response(saved, List.of(), List.of());
    }

    @Transactional
    public SupplierResponse update(Authentication authentication, UUID restaurantId, UUID supplierId, SupplierRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Supplier supplier = requireSupplier(restaurantId, supplierId);
        assertNameFree(restaurantId, request.getName(), supplierId);
        String before = supplier.getName();
        supplier.setUpdatedBy(restaurantScopeService.currentUserId(authentication));
        apply(supplier, request);
        Supplier saved = supplierRepository.saveAndFlush(supplier);
        if (!saved.getName().equals(before)) supplierRepository.renameOnItems(saved.getId(), saved.getName());
        inventoryChangeNotifier.changedAfterCommit(restaurantId);
        return withDetails(saved);
    }

    @Transactional
    public SupplierResponse savePlace(Authentication authentication, UUID restaurantId, UUID supplierId, UUID placeId, SupplierPlaceRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Supplier supplier = requireSupplier(restaurantId, supplierId);
        SupplierPlace place;
        if (placeId != null) {
            place = placeRepository.findByIdAndSupplierId(placeId, supplierId)
                    .orElseThrow(() -> new AuthException("That place isn't one of this supplier's", HttpStatus.NOT_FOUND));
        } else {
            if (request.getId() != null && placeRepository.existsById(request.getId())) {
                throw new AuthException("This place was already added", HttpStatus.CONFLICT);
            }
            place = new SupplierPlace();
            place.setId(request.getId());
            place.setSupplierId(supplierId);
        }
        place.setName(request.getName());
        place.setAddress(request.getAddress());
        place.setPhone(request.getPhone());
        place.setDeliveryDays(request.getDeliveryDays() == null || request.getDeliveryDays().isEmpty() ? null
                : request.getDeliveryDays().stream().sorted().map(String::valueOf).collect(Collectors.joining(",")));
        placeRepository.saveAndFlush(place);
        return withDetails(supplier);
    }

    @Transactional
    public SupplierResponse removePlace(Authentication authentication, UUID restaurantId, UUID supplierId, UUID placeId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Supplier supplier = requireSupplier(restaurantId, supplierId);
        placeRepository.findByIdAndSupplierId(placeId, supplierId).ifPresent(placeRepository::delete);
        return withDetails(supplier);
    }

    /** Links one of your items to the supplier, or changes their code and price for it. */
    @Transactional
    public SupplierResponse saveItem(Authentication authentication, UUID restaurantId, UUID supplierId, UUID itemId, SupplierItemRequest request) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Supplier supplier = requireSupplier(restaurantId, supplierId);
        inventoryItemRepository.findByIdAndRestaurant_IdAndDeletedAtIsNull(itemId, restaurantId).orElseThrow(InventoryItemNotFoundException::new);
        SupplierItemLink link = linkRepository.findBySupplierIdAndInventoryItemId(supplierId, itemId).orElseGet(() -> {
            SupplierItemLink created = new SupplierItemLink();
            created.setSupplierId(supplierId);
            created.setInventoryItemId(itemId);
            return created;
        });
        link.setCode(request.getCode());
        link.setPrice(request.getPrice());
        linkRepository.saveAndFlush(link);
        return withDetails(supplier);
    }

    @Transactional
    public SupplierResponse removeItem(Authentication authentication, UUID restaurantId, UUID supplierId, UUID itemId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        Supplier supplier = requireSupplier(restaurantId, supplierId);
        linkRepository.findBySupplierIdAndInventoryItemId(supplierId, itemId).ifPresent(linkRepository::delete);
        return withDetails(supplier);
    }

    /**
     * Sets a stock item's supplier: one of the restaurant's suppliers by id, or (for older clients) by the typed name,
     * which is found or added as a supplier. Nothing given means no supplier. The item keeps the supplier's name too.
     */
    public void attachToItem(UUID restaurantId, InventoryItem item, UUID supplierId, String supplierName, UUID actorId) {
        Supplier supplier = null;
        if (supplierId != null) {
            supplier = supplierRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(supplierId, restaurantId)
                    .orElseThrow(() -> new AuthException("Choose one of your suppliers", HttpStatus.BAD_REQUEST));
        } else if (supplierName != null && !supplierName.isBlank()) {
            String name = supplierName.trim().replaceAll("\\s+", " ");
            supplier = supplierRepository.findLiveByName(restaurantId, name).orElseGet(() -> {
                Supplier created = new Supplier();
                created.setRestaurantId(restaurantId);
                created.setName(name);
                created.setCreatedBy(actorId);
                return supplierRepository.saveAndFlush(created);
            });
        }
        item.setSupplierId(supplier == null ? null : supplier.getId());
        item.setSupplierName(supplier == null ? null : supplier.getName());
    }

    /** After an item is saved with a supplier: it's one of the items bought from them, with its supplier code. */
    public void linkSavedItem(InventoryItem item) {
        if (item.getSupplierId() == null) return;
        SupplierItemLink link = linkRepository.findBySupplierIdAndInventoryItemId(item.getSupplierId(), item.getId()).orElseGet(() -> {
            SupplierItemLink created = new SupplierItemLink();
            created.setSupplierId(item.getSupplierId());
            created.setInventoryItemId(item.getId());
            created.setPrice(item.getCostPerUnit() == null || item.getCostPerUnit().signum() == 0 ? null : item.getCostPerUnit());
            return created;
        });
        if (item.getSupplierSku() != null) link.setCode(item.getSupplierSku());
        linkRepository.save(link);
    }

    private void apply(Supplier supplier, SupplierRequest request) {
        supplier.setName(request.getName());
        supplier.setContactName(request.getContactName());
        supplier.setPhone(request.getPhone());
        supplier.setEmail(request.getEmail());
        supplier.setAddress(request.getAddress());
        supplier.setNotes(request.getNotes());
        if (request.getActive() != null) supplier.setActive(request.getActive());
    }

    private void assertNameFree(UUID restaurantId, String name, UUID ownId) {
        if (name == null) return;
        supplierRepository.findLiveByName(restaurantId, name.trim().replaceAll("\\s+", " "))
                .filter(other -> !other.getId().equals(ownId))
                .ifPresent(other -> {
                    throw new AuthException("There's already a supplier called " + other.getName(), HttpStatus.CONFLICT);
                });
    }

    private Supplier requireSupplier(UUID restaurantId, UUID supplierId) {
        return supplierRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(supplierId, restaurantId)
                .orElseThrow(() -> new AuthException("Supplier not found", HttpStatus.NOT_FOUND));
    }

    private SupplierResponse withDetails(Supplier supplier) {
        Set<UUID> one = Set.of(supplier.getId());
        return response(supplier, placeRepository.findAllBySupplierIdInOrderByCreatedAtAsc(one), linkRepository.findAllBySupplierIdIn(one));
    }

    private static SupplierResponse response(Supplier s, List<SupplierPlace> places, List<SupplierItemLink> links) {
        return new SupplierResponse(s.getId(), s.getName(), s.getContactName(), s.getPhone(), s.getEmail(), s.getAddress(), s.getNotes(), s.isActive(),
                places.stream().map(p -> new SupplierResponse.Place(p.getId(), p.getName(), p.getAddress(), p.getPhone(), days(p.getDeliveryDays()))).toList(),
                links.stream().sorted(Comparator.comparing(SupplierItemLink::getCreatedAt))
                        .map(l -> new SupplierResponse.Item(l.getInventoryItemId(), l.getCode(), l.getPrice())).toList());
    }

    private static List<Integer> days(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split(",")).map(String::trim).filter(d -> !d.isEmpty()).map(Integer::valueOf).toList();
    }
}
