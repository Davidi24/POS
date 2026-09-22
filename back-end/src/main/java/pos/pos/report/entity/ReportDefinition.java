package pos.pos.report.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.report.enums.ReportFormat;
import pos.pos.report.enums.ReportFrequency;
import pos.pos.report.enums.ReportType;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "report_definitions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_report_definitions_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_report_definitions_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_report_definitions_branch_id", columnList = "branch_id"),
                @Index(name = "idx_report_definitions_report_type", columnList = "report_type"),
                @Index(name = "idx_report_definitions_frequency", columnList = "frequency"),
                @Index(name = "idx_report_definitions_next_run_at", columnList = "next_run_at"),
                @Index(name = "idx_report_definitions_created_by", columnList = "created_by"),
                @Index(name = "idx_report_definitions_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND report_type IN (
            'SALES_SUMMARY',
            'SHIFT_SUMMARY',
            'PAYMENT_SUMMARY',
            'INVENTORY_VALUATION',
            'INVENTORY_VARIANCE',
            'KITCHEN_PERFORMANCE',
            'MENU_PERFORMANCE',
            'LABOR_COST',
            'AUDIT_ACTIVITY',
            'EXCEPTION_REPORT'
        )
        AND frequency IN ('ON_DEMAND', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY')
        AND format IN ('PDF', 'CSV', 'XLSX', 'JSON')
        AND (
            frequency = 'ON_DEMAND'
            OR schedule_expression IS NOT NULL
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ReportDefinition extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_report_definitions_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_report_definitions_branch")
    )
    private Branch branch;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 40)
    private ReportType reportType = ReportType.SALES_SUMMARY;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 20)
    private ReportFrequency frequency = ReportFrequency.ON_DEMAND;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 20)
    private ReportFormat format = ReportFormat.PDF;

    @Column(name = "schedule_expression", length = 100)
    private String scheduleExpression;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "recipient_list", columnDefinition = "text")
    private String recipientList;

    @Column(name = "filter_payload", columnDefinition = "text")
    private String filterPayload;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_run_at", columnDefinition = "timestamptz")
    private OffsetDateTime lastRunAt;

    @Column(name = "next_run_at", columnDefinition = "timestamptz")
    private OffsetDateTime nextRunAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_report_definitions_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_report_definitions_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "reportDefinition")
    private List<ReportExecution> executions = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalizeCode(code == null ? name : code, 80);
        name = NormalizationUtils.normalize(name);
        scheduleExpression = NormalizationUtils.normalize(scheduleExpression);
        timezone = NormalizationUtils.normalize(timezone == null && restaurant != null ? restaurant.getTimezone() : timezone);
        recipientList = NormalizationUtils.normalize(recipientList);
        filterPayload = NormalizationUtils.normalize(filterPayload);
    }

    @Override
    protected void validateState() {
        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("report definition branch must belong to the same restaurant");
            }
        }

        if (frequency != ReportFrequency.ON_DEMAND && scheduleExpression == null) {
            throw new IllegalStateException("scheduled reports require scheduleExpression");
        }
    }
}
