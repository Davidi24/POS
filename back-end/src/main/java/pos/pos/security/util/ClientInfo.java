package pos.pos.security.util;

public record ClientInfo(
        String ipAddress,
        String userAgent
) {
}