package pos.pos.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pos.pos.notification.enums.NotificationChannel;

@Getter
@Setter
public class NotificationPreferenceRequest {

    @NotNull
    private NotificationChannel channel;

    @NotBlank
    private String eventCode;

    @NotNull
    private Boolean enabled;
}
