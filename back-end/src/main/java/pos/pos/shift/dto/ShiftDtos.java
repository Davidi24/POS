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
    /** How someone is paid: an hourly wage or a monthly salary, one of the two. */
    public record PayRate(@DecimalMin("0.00") @DecimalMax("10000000.00") @Digits(integer = 10, fraction = 2) java.math.BigDecimal hourlyRate,
                          @DecimalMin("0.00") @DecimalMax("10000000.00") @Digits(integer = 10, fraction = 2) java.math.BigDecimal monthlySalary) {}
    public record ShiftPay(UUID shiftId, LocalDate date, ShiftStatus status, OffsetDateTime startedAt, OffsetDateTime endedAt,
                           long workedMinutes, long breakMinutes, java.math.BigDecimal hourlyRate, java.math.BigDecimal wages) {}
    public record DayTips(LocalDate date, java.math.BigDecimal tips) {}
    public record StaffPay(UUID userId, String name, List<String> roles, java.math.BigDecimal hourlyRate, int shiftCount,
                           long workedMinutes, long breakMinutes, java.math.BigDecimal wages, java.math.BigDecimal tips,
                           java.math.BigDecimal total, boolean missingRate, List<ShiftPay> shifts, List<DayTips> tipsByDay,
                           String payTeam, String payTeamName, String payTeamPicture, java.math.BigDecimal monthlySalary) {}
    /** A usual shift to save: name, start and end (an end at or before the start is the next day), and the roles it's for. */
    public record TemplateRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 60) String name,
                                  @NotNull java.time.LocalTime start, @NotNull java.time.LocalTime end, List<UUID> roleIds,
                                  @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9_]{1,40}", message = "Choose an icon") String icon) {}
    /** Someone a shift applies to, and the turn they start on (0 = this shift). */
    public record PlanPerson(@NotNull UUID userId, @Min(0) @Max(3) int startTurn) {}
    /** How a shift runs at a branch: weekdays (0 = Monday; none = off the calendar), start, length (no unit = forever), who, and the shifts it takes turns with. */
    public record PlanRequest(Set<@NotNull @Min(0) @Max(6) Integer> weekdays, LocalDate startsOn,
                              @Pattern(regexp = "DAYS|WEEKS|MONTHS", message = "Choose days, weeks or months") String lengthUnit,
                              @Min(1) @Max(366) Integer lengthCount, @Size(max = 200) List<@NotNull @jakarta.validation.Valid PlanPerson> people,
                              @Min(1) @Max(12) Integer turnEvery, @Size(max = 3) List<@NotNull UUID> turnShifts) {}
    public record PlanSaved(Template template, int added, int skipped) {}
    /** A paid role a shift can be for: its code (built-in roles) and the picture a restaurant picked for its own role. */
    public record TemplateRole(UUID id, String name, String code, String picture) {}
    /** A usual shift: who it's for (no roles = everyone). */
    public record Template(UUID id, String name, java.time.LocalTime start, java.time.LocalTime end, List<TemplateRole> roles, String icon,
                           List<Integer> weekdays, LocalDate startsOn, String lengthUnit, Integer lengthCount, List<PlanPerson> people, Integer turnEvery, List<UUID> turnShifts) {}
    /** The usual shifts, and the paid roles a shift can be for. */
    public record Templates(List<Template> templates, List<TemplateRole> roles) {}
    /** A restaurant's own paid role as a Salary team: "ROLE:<id>", its name and picture. */
    public record PayTeamInfo(String key, String name, String picture) {}
    public record PayReport(String timezone, String currency, LocalDate from, LocalDate to, OffsetDateTime serverNow,
                            List<StaffPay> staff, List<PayTeamInfo> teams) {}
    public record Correction(@PositiveOrZero long version, @NotNull OffsetDateTime startedAt,
                             @NotNull OffsetDateTime endedAt, @NotBlank @Size(max=2000) String reason) {}
}
