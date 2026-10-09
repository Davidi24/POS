package pos.pos.menu.mapper;

import org.springframework.security.core.Authentication;
import pos.pos.common.dto.PageResponse;
import pos.pos.menu.dto.response.MenuCatalogResponse;
import pos.pos.menu.dto.response.MenuResponse;
import pos.pos.menu.dto.response.MenuViewResponse;

import java.util.List;
import java.util.Set;

/**
 * Selects the management or operational menu response from granted
 * permissions. Using permissions keeps custom roles working correctly.
 */
public final class MenuRoleResponseMapper {

    private static final Set<String> MANAGEMENT_AUTHORITIES = Set.of(
            "MENUS_CREATE",
            "MENUS_UPDATE",
            "MENUS_DELETE"
    );

    private MenuRoleResponseMapper() {
    }

    public static PageResponse<MenuViewResponse> forActor(
            Authentication authentication,
            PageResponse<MenuResponse> response
    ) {
        List<MenuViewResponse> items = response.getItems() == null
                ? List.of()
                : response.getItems().stream()
                .map(item -> forActor(authentication, item))
                .toList();

        return PageResponse.<MenuViewResponse>builder()
                .items(items)
                .page(response.getPage())
                .size(response.getSize())
                .totalElements(response.getTotalElements())
                .totalPages(response.getTotalPages())
                .hasNext(response.isHasNext())
                .hasPrevious(response.isHasPrevious())
                .build();
    }

    public static MenuViewResponse forActor(
            Authentication authentication,
            MenuResponse response
    ) {
        if (hasManagementAccess(authentication)) {
            return response;
        }

        return MenuCatalogResponse.builder()
                .id(response.getId())
                .code(response.getCode())
                .name(response.getName())
                .description(response.getDescription())
                .active(response.getActive())
                .displayOrder(response.getDisplayOrder())
                .sections(response.getSections())
                .build();
    }

    private static boolean hasManagementAccess(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(MANAGEMENT_AUTHORITIES::contains);
    }
}
