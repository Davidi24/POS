package pos.pos.fraud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.fraud.FraudReviewStatus;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

/** An owner's verdict on one flagged action. The alert itself is recomputed from orders and payments. */
@Entity
@Table(
        name = "fraud_alert_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_fraud_alert_reviews_key", columnNames = {"restaurant_id", "alert_key"}),
        indexes = @Index(name = "idx_fraud_alert_reviews_restaurant", columnList = "restaurant_id")
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class FraudAlertReview extends AbstractTimestampedEntity {

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "alert_key", nullable = false, length = 200)
    private String alertKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FraudReviewStatus status = FraudReviewStatus.OPEN;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "reviewed_by", columnDefinition = "uuid")
    private UUID reviewedBy;

    @Column(name = "reviewed_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime reviewedAt;

    @Override
    protected void normalizeFields() {
        note = NormalizationUtils.normalize(note);
        alertKey = alertKey == null ? null : alertKey.trim();
    }

    @Override
    protected void validateState() {
        if (alertKey == null || alertKey.isEmpty() || alertKey.length() > 200) {
            throw new IllegalStateException("alertKey must be 1 to 200 characters");
        }
        if (reviewedAt == null) {
            throw new IllegalStateException("reviewedAt is required");
        }
    }
}
