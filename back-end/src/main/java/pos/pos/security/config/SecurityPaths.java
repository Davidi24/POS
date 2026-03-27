package pos.pos.security.config;

public class SecurityPaths {

    public static final String[] PUBLIC = {
            "/auth/web/login",
            "/auth/web/refresh",
            "/auth/web/logout",
            "/auth/web/logout-all",
            "/auth/device/login",
            "/auth/device/refresh",
            "/auth/device/logout",
            "/auth/device/logout-all",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/verify-email",
            "/auth/resend-verification",

            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };
}
