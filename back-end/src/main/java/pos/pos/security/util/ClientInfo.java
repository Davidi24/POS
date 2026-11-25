package pos.pos.security.util;

/**
 * Who a request came from: its network address, its user agent and, for sign-in, the app's own id for the device (null when
 * not sent, e.g. the web), used for the per-device sign-in limit, and the device's name as the app gives it ("Samsung
 * SM-G998U", "Linux computer"), shown in the person's list of signed-in devices.
 */
public record ClientInfo(
        String ipAddress,
        String userAgent,
        String deviceId,
        String deviceName,
        // What the device runs ("Linux 6.17", "Android 14"), from the app's X-Device-System (2026-11-25).
        String deviceSystem
) {
    public ClientInfo(String ipAddress, String userAgent) {
        this(ipAddress, userAgent, null, null, null);
    }

    public ClientInfo(String ipAddress, String userAgent, String deviceId) {
        this(ipAddress, userAgent, deviceId, null, null);
    }

    public ClientInfo(String ipAddress, String userAgent, String deviceId, String deviceName) {
        this(ipAddress, userAgent, deviceId, deviceName, null);
    }

    public ClientInfo withDeviceId(String deviceId) {
        return new ClientInfo(ipAddress, userAgent, deviceId, deviceName, deviceSystem);
    }

    public ClientInfo withDevice(String deviceId, String deviceName) {
        return new ClientInfo(ipAddress, userAgent, deviceId, deviceName, deviceSystem);
    }
}
