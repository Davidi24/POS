package pos.pos.device.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "devices",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_devices_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_devices_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_devices_branch_id", columnList = "branch_id"),
                @Index(name = "idx_devices_device_type", columnList = "device_type"),
                @Index(name = "idx_devices_status", columnList = "status"),
                @Index(name = "idx_devices_last_seen_at", columnList = "last_seen_at"),
                @Index(name = "idx_devices_serial_number", columnList = "serial_number"),
                @Index(name = "idx_devices_mac_address", columnList = "mac_address"),
                @Index(name = "idx_devices_created_by", columnList = "created_by"),
                @Index(name = "idx_devices_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND char_length(btrim(device_type)) > 0
        AND char_length(btrim(status)) > 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Device extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_devices_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_devices_branch")
    )
    private Branch branch;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "device_type", nullable = false, length = 30)
    private String deviceType;

    @Column(name = "manufacturer", length = 100)
    private String manufacturer;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "platform", length = 50)
    private String platform;

    @Column(name = "os_version", length = 50)
    private String osVersion;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "PROVISIONING";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_online", nullable = false)
    private boolean online = false;

    @Column(name = "auth_secret_hash", columnDefinition = "text")
    private String authSecretHash;

    @Column(name = "auth_secret_rotated_at", columnDefinition = "timestamptz")
    private OffsetDateTime authSecretRotatedAt;

    @Column(name = "last_seen_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastSeenAt;

    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;

    @Column(name = "mac_address", length = 50)
    private String macAddress;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeviceAssignment> assignments = new ArrayList<>();

    @OneToOne(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private DevicePrinterProfile printerProfile;

    public void addAssignment(DeviceAssignment assignment) {
        if (assignment == null) {
            return;
        }

        assignments.add(assignment);
        assignment.setDevice(this);
    }

    public void removeAssignment(DeviceAssignment assignment) {
        if (assignment == null) {
            return;
        }

        assignments.remove(assignment);
        assignment.setDevice(null);
    }

    public void setPrinterProfile(DevicePrinterProfile printerProfile) {
        if (this.printerProfile != null) {
            this.printerProfile.setDevice(null);
        }

        this.printerProfile = printerProfile;

        if (printerProfile != null) {
            printerProfile.setDevice(this);
        }
    }

    @Override
    protected void normalizeFields() {
        code = normalizeCode(code == null ? name : code);
        name = NormalizationUtils.normalize(name);
        deviceType = normalizeToken(deviceType);
        manufacturer = NormalizationUtils.normalize(manufacturer);
        model = NormalizationUtils.normalize(model);
        serialNumber = NormalizationUtils.normalizeUpper(serialNumber);
        platform = NormalizationUtils.normalize(platform);
        osVersion = NormalizationUtils.normalize(osVersion);
        appVersion = NormalizationUtils.normalize(appVersion);
        status = normalizeToken(status);
        authSecretHash = NormalizationUtils.normalize(authSecretHash);
        ipAddress = NormalizationUtils.normalize(ipAddress);
        macAddress = normalizeMacAddress(macAddress);
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("device branch must belong to the same restaurant");
            }
        }

        if (authSecretRotatedAt != null && authSecretHash == null) {
            throw new IllegalStateException("authSecretRotatedAt requires authSecretHash");
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

    private String normalizeToken(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        return normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String normalizeMacAddress(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String hex = normalized.replaceAll("[^A-F0-9]", "");
        if (hex.length() != 12 && hex.length() != 16) {
            return normalized;
        }

        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < hex.length(); index += 2) {
            if (index > 0) {
                builder.append(':');
            }
            builder.append(hex, index, index + 2);
        }
        return builder.toString();
    }
}
