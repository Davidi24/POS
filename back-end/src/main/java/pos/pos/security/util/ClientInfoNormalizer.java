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
                normalizeUserAgent(clientInfo.userAgent())
        );
    }

    public static String normalizeIpAddress(String ipAddress) {
        return NormalizationUtils.normalize(ipAddress);
    }

    public static String normalizeUserAgent(String userAgent) {
        return NormalizationUtils.normalize(userAgent);
    }
}
