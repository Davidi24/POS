package pos.pos.device.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.device.enums.PrinterConnectionType;
import pos.pos.utils.NormalizationUtils;

@Entity
@Table(
        name = "\"device-printer-profiles\"",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_printer_profiles_device_id", columnNames = "device_id")
        }
)
@Check(constraints = """
        paper_width_mm > 0
        AND (printer_port IS NULL OR (printer_port BETWEEN 1 AND 65535))
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class DevicePrinterProfile extends AbstractTimestampedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "device_id",
            nullable = false,
            unique = true,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_device_printer_profiles_device")
    )
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 30)
    private PrinterConnectionType connectionType = PrinterConnectionType.NETWORK;

    @Column(name = "paper_width_mm", nullable = false)
    private int paperWidthMm = 80;

    @Column(name = "printer_ip", columnDefinition = "inet")
    private String printerIp;

    @Column(name = "printer_port")
    private Integer printerPort;

    @Column(name = "auto_cut", nullable = false)
    private boolean autoCut = true;

    @Column(name = "cash_drawer_kick_enabled", nullable = false)
    private boolean cashDrawerKickEnabled = false;

    @Override
    protected void normalizeFields() {
        printerIp = NormalizationUtils.normalize(printerIp);
    }

    @Override
    protected void validateState() {
        if (paperWidthMm <= 0) {
            throw new IllegalStateException("paperWidthMm must be greater than zero");
        }

        if (printerPort != null && (printerPort < 1 || printerPort > 65_535)) {
            throw new IllegalStateException("printerPort must be between 1 and 65535");
        }

        if (connectionType == PrinterConnectionType.NETWORK && (printerIp == null || printerPort == null)) {
            throw new IllegalStateException("network printers require printerIp and printerPort");
        }
    }
}
