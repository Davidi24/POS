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
        String deviceName
) {
    public ClientInfo(String ipAddress, String userAgent) {
        this(ipAddress, userAgent, null, null);
    }

    public ClientInfo(String ipAddress, String userAgent, String deviceId) {
        this(ipAddress, userAgent, deviceId, null);
    }

    public ClientInfo withDeviceId(String deviceId) {
        return new ClientInfo(ipAddress, userAgent, deviceId, deviceName);
    }

    public ClientInfo withDevice(String deviceId, String deviceName) {
        return new ClientInfo(ipAddress, userAgent, deviceId, deviceName);
    }
}
