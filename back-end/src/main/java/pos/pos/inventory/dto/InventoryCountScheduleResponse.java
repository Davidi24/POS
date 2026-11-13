package pos.pos.inventory.dto;

import pos.pos.settings.enums.InventoryCountPeriod;

import java.time.OffsetDateTime;

public record InventoryCountScheduleResponse(int frequency, InventoryCountPeriod period, OffsetDateTime startedAt) {
}
