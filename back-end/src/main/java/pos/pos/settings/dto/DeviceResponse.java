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
    // Which device it's connected on ("Linux computer (fedora)", "Linux 6.17"), when it says.
    private String connectedDeviceName;
    private String connectedDeviceSystem;
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
    private String systemPrinterName;
    private java.util.UUID hostDeviceId;
    private String printerRole;
    // Whether a printer answers (tills check every minute): ONLINE, OFFLINE or UNCHECKED; when, by which till, and why not.
    private String printerState;
    private OffsetDateTime printerReachedAt;
    private OffsetDateTime printerCheckedAt;
    private String printerCheckedBy;
    private String printerProblem;
    private Boolean autoCut;
    private Boolean cashDrawerKickEnabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
