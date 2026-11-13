package pos.pos.notification.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class NotificationCatalogResponse {

    private OffsetDateTime generatedAt;
    private List<NotificationCapabilityResponse> items;
}
