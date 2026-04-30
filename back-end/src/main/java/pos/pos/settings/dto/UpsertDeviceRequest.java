package pos.pos.settings.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.device.enums.PrinterConnectionType;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertDeviceRequest {

    private UUID branchId;

    @NotBlank(message = "code is required")
    @Size(max = 50, message = "code must be at most 50 characters")
    private String code;

    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @NotBlank(message = "deviceType is required")
    @Size(max = 30, message = "deviceType must be at most 30 characters")
    private String deviceType;

    @Size(max = 100, message = "manufacturer must be at most 100 characters")
    private String manufacturer;

    @Size(max = 100, message = "model must be at most 100 characters")
    private String model;

    @Size(max = 100, message = "serialNumber must be at most 100 characters")
    private String serialNumber;

    @Size(max = 50, message = "platform must be at most 50 characters")
    private String platform;

    @Size(max = 50, message = "osVersion must be at most 50 characters")
    private String osVersion;

    @Size(max = 50, message = "appVersion must be at most 50 characters")
    private String appVersion;

    @NotBlank(message = "status is required")
    @Size(max = 30, message = "status must be at most 30 characters")
    private String status;

    @NotNull(message = "active is required")
    private Boolean active;

    @NotNull(message = "online is required")
    private Boolean online;

    private String ipAddress;

    @Size(max = 50, message = "macAddress must be at most 50 characters")
    private String macAddress;

    private String notes;

    private PrinterConnectionType printerConnectionType;

    @Min(value = 1, message = "paperWidthMm must be greater than 0")
    private Integer paperWidthMm;

    private String printerIp;

    @Min(value = 1, message = "printerPort must be between 1 and 65535")
    @Max(value = 65535, message = "printerPort must be between 1 and 65535")
    private Integer printerPort;

    private Boolean autoCut;

    private Boolean cashDrawerKickEnabled;
}
