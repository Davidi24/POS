package pos.pos.shift.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// How a usual shift runs at a branch: weekdays, start, length, who it applies to and the shifts it takes turns with.
@Entity
@Table(name = "shift_plans")
@Getter
@Setter
@NoArgsConstructor
public class ShiftPlan {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "template_id", nullable = false, columnDefinition = "uuid")
    private UUID templateId;

    @Column(name = "branch_id", nullable = false, columnDefinition = "uuid")
    private UUID branchId;

    // "0,1,2,3,4", 0 = Monday.
    @Column(name = "weekdays", nullable = false, length = 13)
    private String weekdays;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "length_unit", length = 10)
    private String lengthUnit;

    @Column(name = "length_count")
    private Integer lengthCount;

    @Column(name = "turn_every", nullable = false)
    private int turnEvery = 1;

    // The other shifts it takes turns with, comma separated, in order after this one.
    @Column(name = "turn_shifts", columnDefinition = "text")
    private String turnShifts;

    @Column(name = "planned_until")
    private LocalDate plannedUntil;

    // Who it applies to, with the turn each starts on.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shift_plan_people", joinColumns = @JoinColumn(name = "plan_id"))
    @MapKeyColumn(name = "user_id")
    @Column(name = "start_turn")
    private Map<UUID, Integer> people = new HashMap<>();

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    public Set<Integer> weekdaySet() {
        return Arrays.stream(weekdays.split(",")).map(Integer::valueOf).collect(Collectors.toSet());
    }

    public List<UUID> turnList() {
        return turnShifts == null || turnShifts.isBlank() ? List.of() : Arrays.stream(turnShifts.split(",")).map(UUID::fromString).toList();
    }

    /** The last day it runs; null = forever. */
    public LocalDate lastDay() {
        if (lengthUnit == null || lengthCount == null) return null;
        return switch (lengthUnit) {
            case "DAYS" -> startsOn.plusDays(lengthCount - 1L);
            case "MONTHS" -> startsOn.plusMonths(lengthCount).minusDays(1);
            default -> startsOn.plusWeeks(lengthCount).minusDays(1);
        };
    }

    @PrePersist
    protected void created() {
        if (id == null) id = UuidCreator.getTimeOrdered();
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void touched() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
