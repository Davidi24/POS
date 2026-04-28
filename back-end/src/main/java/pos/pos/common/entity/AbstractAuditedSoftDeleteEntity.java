package pos.pos.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public abstract class AbstractAuditedSoftDeleteEntity extends AbstractAuditedEntity {

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;
}
