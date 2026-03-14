package pos.pos.auth.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class LogoutAllRequest {

    private UUID userId;
}
