package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.utils.NormalizationUtils;

/**
 * Represents the classification of an option group.
 *
 * Defines how selections behave,
 * such as single-select or multi-select.
 *
 * Used to categorize option groups.
 */

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "`option-group-types`",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_option_group_type_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_option_group_type_name", columnNames = "name")
        },
        indexes = {
                @Index(name = "idx_option_group_type_code", columnList = "code"),
                @Index(name = "idx_option_group_type_name", columnList = "name")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        """)
public class OptionGroupType extends AbstractTimestampedEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Override
    protected void normalizeFields() {
        code = normalizeCode(code == null ? name : code);
        name = NormalizationUtils.normalize(name);
        description = NormalizationUtils.normalize(description);
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
}
