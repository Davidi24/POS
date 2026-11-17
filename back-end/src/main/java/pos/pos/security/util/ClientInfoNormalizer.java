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
                normalizeDeviceId(clientInfo.deviceId())
        );
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
