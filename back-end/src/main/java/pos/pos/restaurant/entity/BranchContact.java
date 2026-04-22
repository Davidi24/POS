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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedSoftDeleteEntity;
import pos.pos.restaurant.enums.ContactType;
import pos.pos.utils.NormalizationUtils;

@Entity
@Table(
        name = "`branch-contacts`",
        indexes = {
                @Index(name = "idx_branch_contacts_branch_id", columnList = "branch_id"),
                @Index(name = "idx_branch_contacts_type", columnList = "contact_type"),
                @Index(name = "idx_branch_contacts_is_primary", columnList = "is_primary"),
                @Index(name = "idx_branch_contacts_created_by", columnList = "created_by"),
                @Index(name = "idx_branch_contacts_updated_by", columnList = "updated_by"),
                @Index(name = "idx_branch_contacts_deleted_at", columnList = "deleted_at")
        }
)
@Check(constraints = """
        char_length(btrim(full_name)) > 0
        AND contact_type IN ('GENERAL', 'OWNER', 'MANAGER', 'ACCOUNTING', 'SUPPORT')
        """)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class BranchContact extends AbstractAuditedSoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_branch_contacts_branch")
    )
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 30)
    private ContactType contactType;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Override
    protected void normalizeFields() {
        fullName = NormalizationUtils.normalize(fullName);
        email = NormalizationUtils.normalizeLower(email);
        phone = NormalizationUtils.normalize(phone);
        jobTitle = NormalizationUtils.normalize(jobTitle);
    }
}
