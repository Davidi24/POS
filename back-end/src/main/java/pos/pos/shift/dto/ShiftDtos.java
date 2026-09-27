package pos.pos.shift.dto;

import jakarta.validation.constraints.*;
import pos.pos.shift.enums.*;
import java.time.*;
import java.util.*;

public final class ShiftDtos {
    private ShiftDtos() {}
    public record Staff(UUID id, String name, List<String> roles) {}
    public record Break(UUID id, ShiftBreakType type, boolean paid, OffsetDateTime startedAt, OffsetDateTime endedAt) {}
    public record Item(UUID id, long version, UUID userId, String userName, ShiftStatus status,
                       OffsetDateTime scheduledStart, OffsetDateTime scheduledEnd, OffsetDateTime startedAt,
                       OffsetDateTime endedAt, long workedMinutes, long breakMinutes, String notes, List<Break> breaks) {}
    public record Board(String timezone, OffsetDateTime serverNow, List<Item> items, Item current, List<Staff> staff, Item clockInShift) {}
    public record Schedule(@NotNull UUID userId, @NotNull OffsetDateTime scheduledStart,
                           @NotNull OffsetDateTime scheduledEnd, @Size(max=2000) String notes, @PositiveOrZero long version) {}
    public record ClockIn(UUID shiftId) {}
    public record Action(@PositiveOrZero long version, @Size(max=2000) String notes) {}
    public record StartBreak(@PositiveOrZero long version, @NotNull ShiftBreakType type) {}
    public record Correction(@PositiveOrZero long version, @NotNull OffsetDateTime startedAt,
                             @NotNull OffsetDateTime endedAt, @NotBlank @Size(max=2000) String reason) {}
}
