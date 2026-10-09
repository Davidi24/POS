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
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.report.enums.ReportExecutionStatus;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "report_executions",
        indexes = {
                @Index(name = "idx_report_executions_report_definition_id", columnList = "report_definition_id"),
                @Index(name = "idx_report_executions_status", columnList = "status"),
                @Index(name = "idx_report_executions_requested_by", columnList = "requested_by"),
                @Index(name = "idx_report_executions_started_at", columnList = "started_at"),
                @Index(name = "idx_report_executions_completed_at", columnList = "completed_at")
        }
)
@Check(constraints = """
        status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')
        AND (row_count IS NULL OR row_count >= 0)
        AND (
            completed_at IS NULL
            OR started_at IS NOT NULL
        )
        AND (
            completed_at IS NULL
            OR completed_at >= started_at
        )
        AND (
            period_end IS NULL
            OR period_start IS NULL
            OR period_end >= period_start
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ReportExecution extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "report_definition_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_report_executions_report_definition")
    )
    private ReportDefinition reportDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "requested_by",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_report_executions_requested_by_user")
    )
    private User requestedByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReportExecutionStatus status = ReportExecutionStatus.QUEUED;

    @Column(name = "period_start", columnDefinition = "timestamptz")
    private OffsetDateTime periodStart;

    @Column(name = "period_end", columnDefinition = "timestamptz")
    private OffsetDateTime periodEnd;

    @Column(name = "started_at", columnDefinition = "timestamptz")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private OffsetDateTime completedAt;

    @Column(name = "storage_uri", columnDefinition = "text")
    private String storageUri;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "file_checksum", length = 64)
    private String fileChecksum;

    @Column(name = "result_payload", columnDefinition = "text")
    private String resultPayload;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Override
    protected void normalizeFields() {
        storageUri = NormalizationUtils.normalize(storageUri);
        fileChecksum = NormalizationUtils.normalize(fileChecksum);
        resultPayload = NormalizationUtils.normalize(resultPayload);
        errorMessage = NormalizationUtils.normalize(errorMessage);
    }

    @Override
    protected void validateState() {
        if (rowCount != null && rowCount < 0) {
            throw new IllegalStateException("rowCount must not be negative");
        }

        if (completedAt != null && startedAt == null) {
            throw new IllegalStateException("completedAt requires startedAt");
        }

        if (completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalStateException("completedAt must not be before startedAt");
        }

        if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
            throw new IllegalStateException("periodEnd must not be before periodStart");
        }
    }
}
