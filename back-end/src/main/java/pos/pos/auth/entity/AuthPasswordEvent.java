package pos.pos.auth.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;
import pos.pos.auth.enums.PasswordEventKind;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One thing the daily password limits count (user, 2026-11-17): a link sent from "Forgot password?", a password changed
 * while signed in, or a limit email. Kept as long as sign-in attempts, then cleaned up. Never exposed through the API.
 */
@Entity
@Table(name = "auth_password_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuthPasswordEvent {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 30)
    private PasswordEventKind kind;

    @Column(name = "happened_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime happenedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
    }
}
