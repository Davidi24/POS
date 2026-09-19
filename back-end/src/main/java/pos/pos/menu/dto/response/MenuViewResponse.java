package pos.pos.menu.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Marker for menu responses whose shape depends on the caller's permissions.
 */
@Schema(
        description = "Menu response selected from the caller's permissions",
        oneOf = {MenuResponse.class, MenuCatalogResponse.class}
)
public interface MenuViewResponse {
}
