package pos.pos.settings.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.device.enums.PrinterConnectionType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private String code;
    private String name;
    private String deviceType;
    private String manufacturer;
    private String model;
    private String serialNumber;
    private String platform;
    private String osVersion;
    private String appVersion;
    private String status;
    private Boolean active;
    // Heard from in the last few minutes (devices that sign in send a heartbeat every minute; user, 2026-11-25: "online, what
    // is not"). Printers keep what was set on them.
    private Boolean online;
    // Connected with a pairing code and not replaced since, and when that was.
    private Boolean connected;
    private OffsetDateTime connectedAt;
    private OffsetDateTime lastSeenAt;
    private String ipAddress;
    // Where its address is ("Tirana, Albania"), from the server's place database (2026-11-25).
    private String place;
    private String macAddress;
    private String notes;
    private PrinterConnectionType printerConnectionType;
    private Integer paperWidthMm;
    private String printerIp;
    private Integer printerPort;
    private Boolean autoCut;
    private Boolean cashDrawerKickEnabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
