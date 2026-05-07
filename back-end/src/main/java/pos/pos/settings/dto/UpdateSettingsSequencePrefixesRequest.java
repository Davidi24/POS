package pos.pos.settings.dto;

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
public class UpdateSettingsSequencePrefixesRequest {

    @Size(max = 20, message = "orderSequencePrefix must be at most 20 characters")
    private String orderSequencePrefix;

    @Size(max = 20, message = "invoiceSequencePrefix must be at most 20 characters")
    private String invoiceSequencePrefix;
}
