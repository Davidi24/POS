package pos.pos.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

@Entity
@Table(
        name = "notification_preferences",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_preferences_user_channel_event", columnNames = {"user_id", "channel", "event_code"})
        },
        indexes = {
                @Index(name = "idx_notification_preferences_user_id", columnList = "user_id"),
                @Index(name = "idx_notification_preferences_channel", columnList = "channel")
        }
)
@Check(constraints = """
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH', 'WEBHOOK')
        AND char_length(btrim(event_code)) > 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationPreference extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_notification_preferences_user")
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Column(name = "event_code", nullable = false, length = 80)
    private String eventCode;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled = true;

    @Override
    protected void normalizeFields() {
        eventCode = NormalizationUtils.normalizeCode(eventCode, 80);
    }
}
