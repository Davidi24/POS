package pos.pos.auth.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    @Size(max = 4096, message = "refreshToken must be at most 4096 characters")
    private String refreshToken;
}
