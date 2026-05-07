package pos.pos.device.enums;

public enum DeviceStatus {
    // PROVISIONING means the device is being set up / registered, but it is not fully ready to use yet.
    PROVISIONING,
    ACTIVE,
    INACTIVE,
    BLOCKED,
    MAINTENANCE,
    RETIRED
}