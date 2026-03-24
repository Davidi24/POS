package pos.pos.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pos.pos.security.config.AppSecurityProperties;

import java.util.List;

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
                String forwardedIp = firstIpFromList(xForwardedFor);
                if (isPresent(forwardedIp)) {
                    return forwardedIp;
                }
            }

            String xRealIp = firstHeaderValue(request, "X-Real-IP");
            if (isPresent(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
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

    private String firstIpFromList(String headerValue) {
        String[] parts = headerValue.split(",");
        for (String part : parts) {
            String ip = normalize(part);
            if (isPresent(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
