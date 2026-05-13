package pos.pos.tables.dto;

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
public class UpdateTableQrCodeRequest {

    @Size(max = 255, message = "qrCodeValue must be at most 255 characters")
    private String qrCodeValue;
}
