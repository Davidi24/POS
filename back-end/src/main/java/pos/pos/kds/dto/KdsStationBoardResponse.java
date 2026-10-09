package pos.pos.kds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.kds.enums.KdsStationType;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsStationBoardResponse {

    private UUID stationId;
    private String stationCode;
    private String stationName;
    private String screenLabel;
    private UUID deviceId;
    private String deviceCode;
    private String deviceName;
    private KdsStationType stationType;
    private Integer displayOrder;
    private Boolean active;
    private Integer activeTicketCount;
    private Integer readyTicketCount;
    private Integer completedTicketCount;
    private List<KdsTicketResponse> tickets;
}
