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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationStatus;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_notifications_branch_id", columnList = "branch_id"),
                @Index(name = "idx_notifications_template_id", columnList = "template_id"),
                @Index(name = "idx_notifications_recipient_user_id", columnList = "recipient_user_id"),
                @Index(name = "idx_notifications_channel", columnList = "channel"),
                @Index(name = "idx_notifications_status", columnList = "status"),
                @Index(name = "idx_notifications_scheduled_at", columnList = "scheduled_at"),
                @Index(name = "idx_notifications_created_by", columnList = "created_by"),
                @Index(name = "idx_notifications_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH', 'WEBHOOK')
        AND status IN ('QUEUED', 'SENT', 'DELIVERED', 'FAILED', 'READ', 'CANCELLED')
        AND priority IN ('LOW', 'NORMAL', 'HIGH', 'CRITICAL')
        AND char_length(btrim(event_code)) > 0
        AND attempt_count >= 0
        AND (
            delivered_at IS NULL
            OR sent_at IS NOT NULL
        )
        AND (
            read_at IS NULL
            OR delivered_at IS NOT NULL
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Notification extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_notifications_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_notifications_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "template_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_notifications_template")
    )
    private NotificationTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "recipient_user_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_notifications_recipient_user")
    )
    private User recipientUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status = NotificationStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "event_code", nullable = false, length = 80)
    private String eventCode;

    @Column(name = "subject", length = 150)
    private String subject;

    @Column(name = "body", columnDefinition = "text")
    private String body;

    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id", columnDefinition = "uuid")
    private UUID referenceId;

    @Column(name = "scheduled_at", columnDefinition = "timestamptz")
    private OffsetDateTime scheduledAt;

    @Column(name = "sent_at", columnDefinition = "timestamptz")
    private OffsetDateTime sentAt;

    @Column(name = "delivered_at", columnDefinition = "timestamptz")
    private OffsetDateTime deliveredAt;

    @Column(name = "read_at", columnDefinition = "timestamptz")
    private OffsetDateTime readAt;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_notifications_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_notifications_updated_by_user")
    )
    private User updatedByUser;

    @Override
    protected void normalizeFields() {
        eventCode = NormalizationUtils.normalizeCode(eventCode, 80);
        subject = NormalizationUtils.normalize(subject);
        body = NormalizationUtils.normalize(body);
        referenceType = NormalizationUtils.normalizeUpper(referenceType);
        failureReason = NormalizationUtils.normalize(failureReason);
    }

    @Override
    protected void validateState() {
        if (attemptCount < 0) {
            throw new IllegalStateException("attemptCount must not be negative");
        }

        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("notification branch must belong to the same restaurant");
            }
        }

        if (restaurant != null && template != null && template.getRestaurant() != null) {
            if (!Objects.equals(template.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("notification template must belong to the same restaurant");
            }
        }

        if (deliveredAt != null && sentAt == null) {
            throw new IllegalStateException("deliveredAt requires sentAt");
        }

        if (readAt != null && deliveredAt == null) {
            throw new IllegalStateException("readAt requires deliveredAt");
        }
    }
}
