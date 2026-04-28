package pos.pos.menu.entity;

import jakarta.persistence.MappedSuperclass;
import pos.pos.common.entity.AbstractTimestampedEntity;

/**
 * Compatibility base for the menu domain while the shared entity hierarchy lives in common.
 */
@MappedSuperclass
public abstract class BaseAuditEntity extends AbstractTimestampedEntity {
}
