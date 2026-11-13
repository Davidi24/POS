package pos.pos.restaurant.currency;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// One change of a restaurant's currency (1 old = rate new).
@Entity
@Table(name = "restaurant_currency_changes")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantCurrencyChange {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "old_currency", nullable = false, length = 3)
    private String oldCurrency;

    @Column(name = "new_currency", nullable = false, length = 3)
    private String newCurrency;

    @Column(name = "rate", nullable = false, precision = 20, scale = 8)
    private BigDecimal rate;

    @Column(name = "rounding_step", nullable = false, precision = 12, scale = 2)
    private BigDecimal roundingStep;

    @Column(name = "values_converted", nullable = false)
    private int valuesConverted;

    @Column(name = "changed_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime changedAt;

    @Column(name = "changed_by", columnDefinition = "uuid")
    private UUID changedBy;

    @PrePersist
    protected void prePersist() {
        if (id == null) id = UuidCreator.getTimeOrdered();
        if (changedAt == null) changedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
