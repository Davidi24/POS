package pos.pos.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pos.pos.security.config.AppSecurityProperties;

import java.util.List;
import java.util.regex.Pattern;

/**
 * NOTE (Proxy & IP Handling):
 *
 * This class extracts the client IP safely in environments with or without a proxy.
 *
 * Current logic assumes a simple setup:
 * - Client → (optional single trusted proxy) → Application
 * - If request comes from a trusted proxy, we read X-Forwarded-For / X-Real-IP
 * - Otherwise, we use request.getRemoteAddr()
 *
 * SECURITY:
 * - Headers like X-Forwarded-For can be faked by clients
 * - We ONLY trust them when the request comes from a configured trusted proxy
 *
 * FUTURE (Advanced Proxy Chains):
 * - In setups with multiple proxies (e.g., Cloudflare → Load Balancer → NGINX → App),
 *   X-Forwarded-For may contain multiple IPs (client, proxy1, proxy2, ...)
 * - Current implementation takes the FIRST valid IP (common and correct for simple setups)
 * - For complex infrastructures, consider:
 *     - Validating the full proxy chain
 *     - Trusting only known proxy hops
 *     - Using stricter forwarding strategies (e.g., ForwardedHeaderFilter or infra-level handling)
 *
 * IMPORTANT:
 * - Backend should NOT be publicly accessible directly in production
 * - Only trusted proxies should be allowed to reach this service
 */

@Component
public class ClientInfoExtractor {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    private static final Pattern IPV6 = Pattern.compile(
            "^(" +
                    "([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +                          // 1:2:3:4:5:6:7:8
                    "([0-9a-fA-F]{1,4}:){1,7}:|" +                                         // 1::
                    "([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +                        // 1::8
                    "([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|" +                // 1::7:8
                    "([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|" +                // 1::6:7:8
                    "([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|" +                // 1::5:6:7:8
                    "([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|" +                // 1::4:5:6:7:8
                    "[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|" +                      // 1::3:4:5:6:7:8
                    ":((:[0-9a-fA-F]{1,4}){1,7}|:)|" +                                     // ::1 or ::
                    "::(ffff(:0{1,4})?:)?((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\\.){3}" +
                    "(25[0-5]|(2[0-4]|1?[0-9])?[0-9])|" +                          // ::ffff:0:255.255.255.255
                    "([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1?[0-9])?[0-9])\\.){3}" +
                    "(25[0-5]|(2[0-4]|1?[0-9])?[0-9])" +                           // 2001:db8::255.255.255.255
                    ")$"
    );

    private final List<String> trustedProxies;

    @Autowired
    public ClientInfoExtractor(AppSecurityProperties properties) {
        this(properties.getTrustedProxies());
    }

    ClientInfoExtractor(List<String> trustedProxies) {
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            throw new IllegalStateException("app.security.trusted-proxies must not be empty");
        }
        this.trustedProxies = trustedProxies;
    }

    public ClientInfo extract(HttpServletRequest request) {
        return new ClientInfo(
                extractIp(request),
                extractUserAgent(request)
        );
    }

    public String extractIp(HttpServletRequest request) {
        String remoteAddr = normalize(request.getRemoteAddr());

        if (isTrustedProxy(remoteAddr)) {

            String xForwardedFor = firstHeaderValue(request, "X-Forwarded-For");
            if (isPresent(xForwardedFor) && !"unknown".equalsIgnoreCase(xForwardedFor)) {
                String forwardedIp = lastIpFromList(xForwardedFor);
                if (isPresent(forwardedIp)) {
                    return forwardedIp;
                }
            }

            String xRealIp = firstHeaderValue(request, "X-Real-IP");
            if (isPresent(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp) && isValidIp(xRealIp)) {
                return xRealIp;
            }
        }

        return remoteAddr;
    }

    public String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return isPresent(userAgent) ? userAgent.trim() : null;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return remoteAddr != null && trustedProxies.contains(remoteAddr);
    }

    private String firstHeaderValue(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        return normalize(value);
    }

    private String lastIpFromList(String headerValue) {
        String[] parts = headerValue.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String ip = normalize(parts[i]);
            if (isPresent(ip) && !"unknown".equalsIgnoreCase(ip) && isValidIp(ip)) {
                return ip;
            }
        }
        return null;
    }

    static boolean isValidIp(String ip) {
        if (ip == null || ip.length() > 45) {
            return false;
        }
        if (IPV4.matcher(ip).matches()) {
            return true;
        }
        return IPV6.matcher(ip).matches();
    }

    private String normalize(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
