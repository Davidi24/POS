package pos.pos.notification.dto;

import jakarta.validation.constraints.Size;
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
    @Size(max = 80, message = "eventCode must be at most 80 characters")
    private String eventCode;

    @NotNull
    private Boolean enabled;
}
