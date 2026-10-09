package pos.pos.notification.dto;

import lombok.Builder;
import lombok.Getter;
import pos.pos.notification.enums.NotificationCapabilityStatus;
import pos.pos.notification.enums.NotificationTopic;

import java.util.List;

@Getter
@Builder
public class NotificationCapabilityResponse {

    private NotificationTopic topic;
    private String description;
    private NotificationCapabilityStatus status;
    private boolean liveStreamSupported;
    private boolean persistentFeedSupported;
    private boolean templateSupported;
    private boolean preferenceSupported;
    private List<String> eventCodes;
    private String notes;
}
