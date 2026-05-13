package pos.pos.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.settings.dto.SettingsTransferRequest;
import pos.pos.utils.NormalizationUtils;

//checked
// template is used for predefined setting like the primary language is eng so when restaurant install my
// app they use them if they want change they change it in the Settings entity then
@Entity
@Table(
        name = "\"settings-templates\"",
        indexes = {
                @Index(name = "idx_settings_templates_created_by", columnList = "created_by"),
                @Index(name = "idx_settings_templates_updated_by", columnList = "updated_by"),
                @Index(name = "idx_settings_templates_name", columnList = "template_name")
        }
)
@Check(constraints = """
        char_length(btrim(template_name)) > 0
        AND jsonb_typeof(payload) = 'object'
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SettingsTemplate extends AbstractAuditedEntity {

    @Column(name = "template_name", nullable = false, length = 150)
    private String templateName;

    @Column(name = "description", length = 500)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private SettingsTransferRequest payload;

    @Override
    protected void normalizeFields() {
        templateName = NormalizationUtils.normalize(templateName);
        description = NormalizationUtils.normalize(description);
    }
}
