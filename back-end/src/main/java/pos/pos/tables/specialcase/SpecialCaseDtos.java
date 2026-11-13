package pos.pos.tables.specialcase;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class SpecialCaseDtos {

    private SpecialCaseDtos() {
    }

    public record MoreTimeRequest(@NotNull @Min(1) @Max(240) Integer minutes) {
    }

    public record MoveBookingRequest(@NotNull @Size(min = 1, max = 8) List<@NotNull UUID> tableIds, UUID primaryTableId) {
    }

    // tableId: another free table, when the one it came from has been taken.
    public record ReturnRequest(UUID tableId) {
    }

    // tableFree: the table it would go back to can take it. orderId: the order to pay after returning it.
    public record ActionResponse(UUID id, SpecialCaseAction.Kind kind, String summary, String detail, String tableNumber, Integer guests,
                                 UUID orderId, OffsetDateTime happenedAt, boolean tableFree) {
    }
}
