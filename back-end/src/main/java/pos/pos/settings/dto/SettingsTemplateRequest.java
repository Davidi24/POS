package pos.pos.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsTemplateRequest {

    @NotBlank(message = "templateName is required")
    @Size(max = 150, message = "templateName must be at most 150 characters")
    private String templateName;

    @Size(max = 500, message = "description must be at most 500 characters")
    private String description;

    @Valid
    @NotNull(message = "payload is required")
    private SettingsTransferRequest payload;
}
