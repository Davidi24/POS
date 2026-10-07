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
    public record Board(String timezone, OffsetDateTime serverNow, List<Item> items, Item current, List<Staff> staff, Item clockInShift, int clockInEarlyMinutes) {}
    public record Schedule(@NotNull UUID userId, @NotNull OffsetDateTime scheduledStart,
                           @NotNull OffsetDateTime scheduledEnd, @Size(max=2000) String notes, @PositiveOrZero long version) {}
    public record ClockIn(UUID shiftId) {}
    public record Action(@PositiveOrZero long version, @Size(max=2000) String notes) {}
    public record StartBreak(@PositiveOrZero long version, @NotNull ShiftBreakType type) {}
    public record PayRate(@NotNull @DecimalMin("0.00") @DecimalMax("10000.00") @Digits(integer = 10, fraction = 2) java.math.BigDecimal hourlyRate) {}
    public record ShiftPay(UUID shiftId, LocalDate date, ShiftStatus status, OffsetDateTime startedAt, OffsetDateTime endedAt,
                           long workedMinutes, long breakMinutes, java.math.BigDecimal hourlyRate, java.math.BigDecimal wages) {}
    public record DayTips(LocalDate date, java.math.BigDecimal tips) {}
    public record StaffPay(UUID userId, String name, List<String> roles, java.math.BigDecimal hourlyRate, int shiftCount,
                           long workedMinutes, long breakMinutes, java.math.BigDecimal wages, java.math.BigDecimal tips,
                           java.math.BigDecimal total, boolean missingRate, List<ShiftPay> shifts, List<DayTips> tipsByDay) {}
    public record PayReport(String timezone, String currency, LocalDate from, LocalDate to, OffsetDateTime serverNow,
                            List<StaffPay> staff) {}
    public record Correction(@PositiveOrZero long version, @NotNull OffsetDateTime startedAt,
                             @NotNull OffsetDateTime endedAt, @NotBlank @Size(max=2000) String reason) {}
}
