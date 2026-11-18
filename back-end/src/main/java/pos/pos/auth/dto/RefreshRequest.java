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

    // Signing out only: why, for the audit log (LOCKED = the Lock button on a till, IDLE = the till locked itself).
    @Size(max = 20, message = "reason must be at most 20 characters")
    private String reason;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
