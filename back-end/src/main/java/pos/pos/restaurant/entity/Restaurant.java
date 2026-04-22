package pos.pos.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedSoftDeleteEntity;
import pos.pos.device.entity.Device;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.OptionGroup;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.settings.entity.Settings;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.util.ArrayList;
import java.util.List;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Restaurant is the business and legal parent of the POS organization.
 *
 * FUTURE RELATION: menu-categories.restaurant_id -> restaurants.id
 * FUTURE RELATION: inventory-items.restaurant_id or branch_id depending on central-vs-branch stock model
 */
@Entity
@Table(
        name = "restaurants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_restaurants_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_restaurants_slug", columnNames = "slug")
        },
        indexes = {
                @Index(name = "idx_restaurants_owner_id", columnList = "owner_id"),
                @Index(name = "idx_restaurants_status", columnList = "status"),
                @Index(name = "idx_restaurants_created_by", columnList = "created_by"),
                @Index(name = "idx_restaurants_updated_by", columnList = "updated_by"),
                @Index(name = "idx_restaurants_deleted_at", columnList = "deleted_at")
        }
)
@Check(constraints = """
        char_length(btrim(name)) > 0
        AND char_length(btrim(legal_name)) > 0
        AND char_length(btrim(code)) > 0
        AND char_length(btrim(slug)) > 0
        AND char_length(currency) = 3
        AND status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'ARCHIVED')
        """)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Restaurant extends AbstractAuditedSoftDeleteEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "slug", nullable = false, length = 150)
    private String slug;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "char(3)")
    private String currency;

    @Column(name = "timezone", nullable = false, length = 100)
    private String timezone;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    @Column(name = "owner_id", columnDefinition = "uuid")
    private UUID ownerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "owner_id",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_restaurants_owner_user")
    )
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_restaurants_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_restaurants_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "restaurant")
    private List<Branch> branches = new ArrayList<>();

    @OneToOne(mappedBy = "restaurant", fetch = FetchType.LAZY)
    private Settings settings;

    @OneToMany(mappedBy = "restaurant")
    private List<Menu> menus = new ArrayList<>();

    @OneToMany(mappedBy = "restaurant")
    private List<OptionGroup> optionGroups = new ArrayList<>();

    @OneToMany(mappedBy = "restaurant")
    private List<Device> devices = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
        legalName = NormalizationUtils.normalize(legalName);
        code = normalizeCode(code == null ? name : code);
        slug = normalizeSlug(slug == null ? name : slug);
        description = NormalizationUtils.normalize(description);
        email = NormalizationUtils.normalizeLower(email);
        phone = NormalizationUtils.normalize(phone);
        website = NormalizationUtils.normalize(website);
        currency = NormalizationUtils.normalizeUpper(currency);
        timezone = NormalizationUtils.normalize(timezone);
    }

    @Override
    protected void validateState() {
        if (timezone == null) {
            return;
        }

        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new IllegalStateException("timezone must be a valid IANA identifier", ex);
        }
    }

    private String normalizeCode(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isEmpty() ? null : sanitized;
    }

    private String normalizeSlug(String value) {
        String normalized = NormalizationUtils.normalizeLower(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");

        return sanitized.isEmpty() ? null : sanitized;
    }
}
