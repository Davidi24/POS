package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsAuditLogResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private String entityType;
    private UUID entityId;
    private String action;
    private String message;
    private UUID actorUserId;
    private OffsetDateTime occurredAt;
}
