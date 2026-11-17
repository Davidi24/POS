package pos.pos.security.util;

/**
 * Who a request came from: its network address, its user agent and, for sign-in, the app's own id for the device (null when
 * not sent, e.g. the web), used for the per-device sign-in limit.
 */
public record ClientInfo(
        String ipAddress,
        String userAgent,
        String deviceId
) {
    public ClientInfo(String ipAddress, String userAgent) {
        this(ipAddress, userAgent, null);
    }

    public ClientInfo withDeviceId(String deviceId) {
        return new ClientInfo(ipAddress, userAgent, deviceId);
    }
}
