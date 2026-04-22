package pos.pos.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import pos.pos.utils.NormalizationUtils;

@Entity
@Table(
        name = "\"settings-receipts\"",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_settings_receipts_settings_id", columnNames = "settings_id")
        }
)
@Check(constraints = "receipt_copies > 0")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SettingsReceipt extends AbstractTimestampedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "settings_id",
            nullable = false,
            unique = true,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_receipts_settings")
    )
    private Settings settings;

    @Column(name = "auto_print_customer_receipt", nullable = false)
    private boolean autoPrintCustomerReceipt = false;

    @Column(name = "auto_print_kitchen_ticket", nullable = false)
    private boolean autoPrintKitchenTicket = false;

    @Column(name = "receipt_copies", nullable = false)
    private int receiptCopies = 1;

    @Column(name = "show_logo", nullable = false)
    private boolean showLogo = true;

    @Column(name = "show_tax_breakdown", nullable = false)
    private boolean showTaxBreakdown = true;

    @Column(name = "show_server_name", nullable = false)
    private boolean showServerName = true;

    @Column(name = "show_table_name", nullable = false)
    private boolean showTableName = true;

    @Column(name = "show_order_number", nullable = false)
    private boolean showOrderNumber = true;

    @Column(name = "show_qr_code", nullable = false)
    private boolean showQrCode = false;

    @Column(name = "print_voided_items", nullable = false)
    private boolean printVoidedItems = false;

    @Column(name = "footer_note", columnDefinition = "text")
    private String footerNote;

    @Override
    protected void normalizeFields() {
        footerNote = NormalizationUtils.normalize(footerNote);
    }

    @Override
    protected void validateState() {
        if (receiptCopies <= 0) {
            throw new IllegalStateException("receiptCopies must be greater than zero");
        }
    }
}
