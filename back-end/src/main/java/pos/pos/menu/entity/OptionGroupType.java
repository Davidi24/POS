package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "option-group-types",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_option_group_type_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_option_group_type_name", columnNames = "name")
        },
        indexes = {
                @Index(name = "idx_option_group_type_code", columnList = "code"),
                @Index(name = "idx_option_group_type_name", columnList = "name")
        }
)
public class OptionGroupType extends BaseAuditEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;
}