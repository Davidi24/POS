package pos.pos.restaurant.receipt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Both receipts' design (the preliminary bill and the fiscal invoice), as the app describes it (user, 2026-11-23: "a small
 * canvas"). The server keeps it whole; pictures in it are stored like the logo and referenced by their links.
 */
@Entity
@Table(name = "restaurant_receipt_designs")
@Getter
@Setter
@NoArgsConstructor
public class RestaurantReceiptDesign {

    @Id
    @Column(name = "restaurant_id", columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "design", nullable = false, columnDefinition = "text")
    private String design;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;
}
