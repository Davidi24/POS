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
public class SettingsExportResponse {

    private String schemaVersion;
    private UUID restaurantId;
    private String restaurantCode;
    private OffsetDateTime exportedAt;
    private SettingsTransferRequest payload;
}
