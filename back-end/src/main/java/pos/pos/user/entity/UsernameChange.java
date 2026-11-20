package pos.pos.user.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One username change a person made themselves (Profile): for the 3-in-30-days limit, reserving the old name, and "was …". */
@Entity
@Table(name = "username_changes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsernameChange {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "old_username", nullable = false, updatable = false, length = 50)
    private String oldUsername;

    @Column(name = "new_username", nullable = false, updatable = false, length = 50)
    private String newUsername;

    @Column(name = "changed_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime changedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UuidCreator.getTimeOrdered();
    }
}
