package pos.pos.utils;

import org.springframework.web.util.UriUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;

public final class FrontendUrlUtils {

    private FrontendUrlUtils() {
    }

    public static String buildTokenUrl(String baseUrl, String path, String rawToken) {
        String encodedToken = UriUtils.encodeQueryParam(rawToken, StandardCharsets.UTF_8);

        return UriComponentsBuilder.fromUriString(baseUrl)
                .path(path)
                .queryParam("token", encodedToken)
                .build(true)
                .toUriString();
    }
}
