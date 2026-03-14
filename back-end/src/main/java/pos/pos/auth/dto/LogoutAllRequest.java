package pos.pos.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class LogoutAllRequest {

    @NotNull
    private UUID userId;
}
