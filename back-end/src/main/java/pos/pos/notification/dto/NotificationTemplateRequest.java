package pos.pos.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pos.pos.notification.enums.NotificationChannel;

@Getter
@Setter
public class NotificationTemplateRequest {

    private String code;

    @NotBlank
    private String name;

    @NotNull
    private NotificationChannel channel;

    private String subjectTemplate;

    private String bodyTemplate;

    @NotNull
    private Boolean active;
}
