package pos.pos.utils;

import org.springframework.web.util.UriComponentsBuilder;

public final class FrontendUrlUtils {

    private FrontendUrlUtils() {
    }

    public static String buildTokenUrl(String baseUrl, String path, String rawToken) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }
}
