package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// A guest's no-shows for the warning on their booking, and the times a manager cleared it.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestHistoryResponse {

    // No-shows counted since the last clear; the warning shows from `warningFrom` of them.
    private Integer noShowCount;
    private Integer warningFrom;
    private List<NoShow> noShows;
    private List<Clear> clears;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoShow {
        private UUID reservationId;
        private String reservationCode;
        private OffsetDateTime reservationStart;
        private Integer partySize;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Clear {
        private OffsetDateTime clearedAt;
        private UUID clearedBy;
        private String reason;
    }
}
