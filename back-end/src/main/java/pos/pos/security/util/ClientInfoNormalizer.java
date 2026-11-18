package pos.pos.security.util;

import pos.pos.utils.NormalizationUtils;

public final class ClientInfoNormalizer {

    private ClientInfoNormalizer() {
    }

    public static ClientInfo normalize(ClientInfo clientInfo) {
        if (clientInfo == null) {
            return null;
        }

        return new ClientInfo(
                normalizeIpAddress(clientInfo.ipAddress()),
                normalizeUserAgent(clientInfo.userAgent()),
                normalizeDeviceId(clientInfo.deviceId()),
                normalizeDeviceName(clientInfo.deviceName())
        );
    }

    /** The device's name: printable characters only, at most 100; blank counts as not sent. */
    public static String normalizeDeviceName(String deviceName) {
        String value = NormalizationUtils.normalize(deviceName);
        if (value == null) return null;
        value = value.replaceAll("\\p{Cntrl}", "").trim();
        if (value.isEmpty()) return null;
        return value.length() > 100 ? value.substring(0, 100) : value;
    }

    /** The app's device id: letters, digits, "-" and "_", at most 100; anything else counts as not sent. */
    public static String normalizeDeviceId(String deviceId) {
        String value = NormalizationUtils.normalize(deviceId);
        if (value == null || value.length() > 100 || !value.matches("[A-Za-z0-9_-]+")) return null;
        return value;
    }

    public static String normalizeIpAddress(String ipAddress) {
        return NormalizationUtils.normalize(ipAddress);
    }

    public static String normalizeUserAgent(String userAgent) {
        return NormalizationUtils.normalize(userAgent);
    }
}
