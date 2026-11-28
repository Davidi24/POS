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
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

@Entity
@Table(
        name = "notification_templates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_notification_templates_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_notification_templates_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_notification_templates_channel", columnList = "channel"),
                @Index(name = "idx_notification_templates_created_by", columnList = "created_by"),
                @Index(name = "idx_notification_templates_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND channel IN ('IN_APP', 'EMAIL', 'SMS', 'PUSH', 'WEBHOOK')
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class NotificationTemplate extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_notification_templates_restaurant")
    )
    private Restaurant restaurant;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Column(name = "subject_template", length = 150)
    private String subjectTemplate;

    @Column(name = "body_template", columnDefinition = "text")
    private String bodyTemplate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_notification_templates_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_notification_templates_updated_by_user")
    )
    private User updatedByUser;

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalizeCode(code == null ? name : code, 80);
        name = NormalizationUtils.normalize(name);
        subjectTemplate = NormalizationUtils.normalize(subjectTemplate);
        bodyTemplate = NormalizationUtils.normalize(bodyTemplate);
    }
}
