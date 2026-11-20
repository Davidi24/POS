package pos.pos.notification.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pos.pos.notification.enums.NotificationChannel;

@Getter
@Setter
public class NotificationTemplateRequest {

    @Size(max = 80, message = "code must be at most 80 characters")
    private String code;

    @NotBlank
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @NotNull
    private NotificationChannel channel;

    @Size(max = 150, message = "subjectTemplate must be at most 150 characters")
    private String subjectTemplate;

    @Size(max = 10000, message = "bodyTemplate must be at most 10000 characters")
    private String bodyTemplate;

    @NotNull
    private Boolean active;
}
