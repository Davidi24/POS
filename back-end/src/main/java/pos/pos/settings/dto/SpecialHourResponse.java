package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialHourResponse {

    private UUID id;
    private UUID branchId;
    private LocalDate specialDate;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Boolean closed;
    private String note;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
