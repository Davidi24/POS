package pos.pos.inventory.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.audit.entity.AuditLog;
import pos.pos.audit.enums.AuditSource;
import pos.pos.audit.repository.AuditLogRepository;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.user.entity.User;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What happened to stock items themselves (added, changed, photo, not in use, deleted) and who did it, for Inventory →
 * History next to the stock changes (user, 2026-10-30: "the history should also show for items if it was created,
 * updated, by who"). Kept in the general audit log (`audit_logs`, entity type INVENTORY_ITEM); the item's name at that
 * moment goes in the metadata, so a renamed or deleted item still reads right.
 */
@Component
@RequiredArgsConstructor
public class InventoryItemEvents {

    public static final String ENTITY = "INVENTORY_ITEM";

    private final AuditLogRepository auditLogRepository;
    private final EntityManager entityManager;

    void record(InventoryItem item, String action, String summary, UUID actorId) {
        AuditLog log = new AuditLog();
        log.setRestaurant(item.getRestaurant());
        log.setActorUser(actorId == null ? null : entityManager.getReference(User.class, actorId));
        log.setSource(AuditSource.BACK_OFFICE);
        log.setEntityType(ENTITY);
        log.setEntityId(item.getId());
        log.setAction(action);
        log.setSummary(summary.length() > 255 ? summary.substring(0, 254) + "…" : summary);
        log.setMetadataPayload(item.getName());
        auditLogRepository.save(log);
    }

    /** The parts of an item a change can touch, taken before the change. */
    record Snapshot(String name, String kind, String unit, BigDecimal cost, BigDecimal reorder, BigDecimal par, String supplier,
                    String barcode, String code, boolean counted, boolean active, String notes) {
        static Snapshot of(InventoryItem item) {
            return new Snapshot(item.getName(), kindOf(item), String.valueOf(item.getBaseUnit()), item.getCostPerUnit(), item.getReorderPoint(),
                    item.getParLevel(), item.getSupplierName(), item.getBarcode(), item.getCode(), item.isTrackInventory(), item.isActive(),
                    item.getStorageNotes());
        }
    }

    /** "Cost 2.50 → 3.00 · Reorder at 5 → 8 · Supplier": what changed, short; empty when nothing did. */
    static String changes(Snapshot before, InventoryItem after) {
        Snapshot now = Snapshot.of(after);
        List<String> parts = new ArrayList<>();
        if (!Objects.equals(before.name(), now.name())) parts.add("Name " + before.name() + " → " + now.name());
        if (!Objects.equals(before.kind(), now.kind())) parts.add("Kind");
        if (!Objects.equals(before.unit(), now.unit())) parts.add("Unit");
        if (differs(before.cost(), now.cost())) parts.add(change("Cost", before.cost(), now.cost()));
        if (differs(before.reorder(), now.reorder())) parts.add(change("Reorder at", before.reorder(), now.reorder()));
        if (differs(before.par(), now.par())) parts.add(change("Full at", before.par(), now.par()));
        if (!Objects.equals(blank(before.supplier()), blank(now.supplier()))) parts.add("Supplier");
        if (!Objects.equals(blank(before.barcode()), blank(now.barcode()))) parts.add("Barcode");
        if (!Objects.equals(before.code(), now.code())) parts.add("Code");
        if (before.counted() != now.counted()) parts.add(now.counted() ? "Counted" : "Not counted");
        if (before.active() != now.active()) parts.add(now.active() ? "In use" : "Not in use");
        if (!Objects.equals(blank(before.notes()), blank(now.notes()))) parts.add("Notes");
        return String.join(" · ", parts);
    }

    private static String kindOf(InventoryItem item) {
        return item.getKind() == null ? String.valueOf(item.getItemType()) : String.valueOf(item.getKind().getId());
    }

    private static boolean differs(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a != b;
        return a.compareTo(b) != 0;
    }

    // "Cost 2.5 → 3", "Reorder at 2" when it was set for the first time, "Full at removed" when it was cleared.
    private static String change(String label, BigDecimal before, BigDecimal after) {
        if (before == null) return label + " " + plain(after);
        if (after == null) return label + " removed";
        return label + " " + plain(before) + " → " + plain(after);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
