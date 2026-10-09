package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.WaitlistStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntryResponse {
    private UUID id;
    private String guestName;
    private String contactPhone;
    private Integer partySize;
    private String note;
    private WaitlistStatus status;
    private OffsetDateTime createdAt;
    private Integer waitedMinutes;
    // A table big enough is free right now.
    private Boolean tableFreeNow;
    // Otherwise, roughly when the first big-enough table should free up (when its seated guests' booking ends).
    private OffsetDateTime tableFreeAround;
    private UUID tableId;
    private OffsetDateTime seatedAt;
}
