package pos.pos.auth.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A PIN suggestion shown, a PIN saved or a PIN reset by a manager: for the daily limits and the audit. */
@Entity
@Table(name = "pin_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PinEvent {

    public enum Kind { SUGGESTED, SET, RESET }

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 20)
    private Kind kind;

    // A suggestion's PIN (as its peppered hash), so only a suggested PIN can be saved.
    @Column(name = "pin_hash", updatable = false, columnDefinition = "text")
    private String pinHash;

    @Column(name = "actor_id", updatable = false, columnDefinition = "uuid")
    private UUID actorId;

    @Column(name = "happened_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime happenedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidCreator.getTimeOrdered();
    }
}
