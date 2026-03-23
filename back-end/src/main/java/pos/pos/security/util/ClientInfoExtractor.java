package pos.pos.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientInfoExtractor {

    public ClientInfo extract(HttpServletRequest request) {
        return new ClientInfo(
                extractIp(request),
                extractUserAgent(request)
        );
    }

    public String extractIp(HttpServletRequest request) {
        String xForwardedFor = firstHeaderValue(request, "X-Forwarded-For");
        if (isPresent(xForwardedFor) && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return firstIpFromList(xForwardedFor);
        }

        String xRealIp = firstHeaderValue(request, "X-Real-IP");
        if (isPresent(xRealIp) && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        String remoteAddr = request.getRemoteAddr();
        return isPresent(remoteAddr) ? remoteAddr : null;
    }

    public String extractUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return isPresent(userAgent) ? userAgent.trim() : null;
    }

    private String firstHeaderValue(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        return isPresent(value) ? value.trim() : null;
    }

    private String firstIpFromList(String headerValue) {
        String[] parts = headerValue.split(",");
        for (String part : parts) {
            String ip = part.trim();
            if (isPresent(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }
        }
        return null;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}