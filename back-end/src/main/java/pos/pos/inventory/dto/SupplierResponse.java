package pos.pos.inventory.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** A supplier with their places and the items you buy from them. */
public record SupplierResponse(
        UUID id, String name, String contactName, String phone, String email, String address, String notes, boolean active,
        List<Place> places, List<Item> items
) {
    public record Place(UUID id, String name, String address, String phone, List<Integer> deliveryDays) {
    }

    public record Item(UUID itemId, String code, BigDecimal price) {
    }
}
