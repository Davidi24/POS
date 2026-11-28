package pos.pos.settings.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePrinterRoutesRequest {

    @NotNull(message = "printerIds is required")
    @Size(max = 100, message = "printerIds can have at most 100 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") UUID> printerIds;
}
