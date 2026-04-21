package pos.pos.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pos.pos.security.config.AppSecurityProperties;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;
/**
 * Extracts reliable client information (IP address and User-Agent) from HTTP requests.
 * <p>
 * Purpose:
 *   Provides a secure way to determine the real client IP when the application is behind
 *   a trusted reverse proxy (e.g., NGINX). Prevents header spoofing by only trusting
 *   forwarding headers if the request originates from a configured trusted proxy.
 * <p>
 * Behavior:
 *   - If the request comes from a trusted proxy:
 *       - Reads client IP from X-Forwarded-For (last valid IP in the list)
 *       - Falls back to X-Real-IP if needed
 *   - If the request does NOT come from a trusted proxy:
 *       - Ignores all forwarding headers and uses remoteAddr
 *   - Invalid or malformed IPs are rejected and logged
 *   - Suspicious header usage (e.g., X-Forwarded-For from untrusted source) is logged
 *   - User-Agent is extracted, trimmed, and safely truncated to a configurable length
 * <p>
 * Security guarantees:
 *   - Prevents X-Forwarded-For spoofing
 *   - Rejects non-IP values (no hostnames, no injections)
 *   - Does not perform DNS resolution for validation
 *   - Fails fast if trusted proxy configuration is missing
 * <p>
 * Requirements:
 *   - Application must be deployed behind a trusted reverse proxy
 *   - app.security.trusted-proxies must contain the proxy IP(s)
 *   - server.forward-headers-strategy must be set to 'none'
 * <p>
 * Limitations (current design):
 *   - Supports only exact proxy IP matching (no CIDR ranges)
 *   - Assumes a single proxy or controlled infrastructure
 *   - Uses "last IP" strategy for X-Forwarded-For (standard for NGINX)
 * <p>
 * Future improvements:
 *   - Support CIDR ranges for trusted proxies (cloud/load balancer setups)
 *   - Support multi-proxy chain validation
 *   - Optional integration with ForwardedHeaderFilter (RFC 7239)
 */

// checked
// tested

@Component
public class ClientInfoExtractor {

    private static final Logger log = LoggerFactory.getLogger(ClientInfoExtractor.class);

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    );

    private static final Pattern IPV6_CHARS = Pattern.compile("[0-9a-fA-F:.]+");

    private final List<String> trustedProxies;
    private final int maxUserAgentLength;

    @Autowired
    public ClientInfoExtractor(AppSecurityProperties properties) {
        this(properties.getTrustedProxies(), properties.getMaxUserAgentLength());
    }

    ClientInfoExtractor(List<String> trustedProxies, int maxUserAgentLength) {
        if (trustedProxies == null || trustedProxies.isEmpty()) {
            throw new IllegalStateException("app.security.trusted-proxies must not be empty");
        }
        this.trustedProxies = trustedProxies;
        this.maxUserAgentLength = maxUserAgentLength;
    }

    public ClientInfo extract(HttpServletRequest request) {
        return new ClientInfo(
                extractIp(request),
                extractUserAgent(request)
        );
    }

    public String extractIp(HttpServletRequest request) {
        String remoteAddr = normalize(request.getRemoteAddr());

        if (remoteAddr == null) {
            log.warn("Request without remote address");
            return null;
        }

        boolean trusted = isTrustedProxy(remoteAddr);

        if (!trusted && request.getHeader("X-Forwarded-For") != null) {
            log.warn("Suspicious request: X-Forwarded-For present but caller is not trusted proxy. remoteAddr={}", remoteAddr);
        }

        if (trusted) {

            String xForwardedFor = firstHeaderValue(request, "X-Forwarded-For");
            if (isPresent(xForwardedFor) && !"unknown".equalsIgnoreCase(xForwardedFor)) {
                String forwardedIp = lastIpFromList(xForwardedFor);
                if (isPresent(forwardedIp)) {
                    return forwardedIp;
                }
            }

            String xRealIp = firstHeaderValue(request, "X-Real-IP");
            if (isPresent(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
                if (!isValidIp(xRealIp)) {
                    log.warn("Rejected invalid IP in X-Real-IP header: '{}'", xRealIp);
                } else {
                    return xRealIp;
                }
            }
        }

        return remoteAddr;
    }

    public String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (!isPresent(userAgent)) {
            return null;
        }

        String trimmed = userAgent.trim();

        if (trimmed.length() > maxUserAgentLength) {
            log.warn("User-Agent header exceeds {} characters, truncating", maxUserAgentLength);
            return trimmed.substring(0, maxUserAgentLength);
        }

        return trimmed;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return remoteAddr != null && trustedProxies.contains(remoteAddr);
    }

    private String firstHeaderValue(HttpServletRequest request, String headerName) {
        return normalize(request.getHeader(headerName));
    }

    private String lastIpFromList(String headerValue) {
        String[] parts = headerValue.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String ip = normalize(parts[i]);

            if (!isPresent(ip) || "unknown".equalsIgnoreCase(ip)) {
                continue;
            }

            if (!isValidIp(ip)) {
                log.warn("Rejected invalid IP in X-Forwarded-For header: '{}'", ip);
                continue;
            }

            return ip;
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

        if (!ip.contains(":") || !IPV6_CHARS.matcher(ip).matches()) {
            return false;
        }

        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.getHostAddress().contains(":");
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private String normalize(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}