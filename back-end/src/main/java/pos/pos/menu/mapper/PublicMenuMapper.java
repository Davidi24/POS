package pos.pos.menu.mapper;

import org.springframework.stereotype.Component;
import pos.pos.menu.dto.PublicMenuItemResponse;
import pos.pos.menu.dto.PublicMenuResponse;
import pos.pos.menu.dto.PublicMenuSectionResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class PublicMenuMapper {

    public PublicMenuResponse toMenuResponse(Menu menu) {
        return toMenuResponse(menu, null, Map.of());
    }

    public PublicMenuResponse toMenuResponse(
            Menu menu,
            List<MenuSection> sections,
            Map<UUID, List<MenuItem>> itemsBySectionId
    ) {
        if (menu == null) {
            return null;
        }

        return PublicMenuResponse.builder()
                .id(menu.getId())
                .code(menu.getCode())
                .name(menu.getName())
                .description(menu.getDescription())
                .displayOrder(menu.getDisplayOrder())
                .sections(sections == null ? null : sections.stream()
                        .map(section -> toSectionResponse(section, itemsBySectionId.get(section.getId())))
                        .toList())
                .build();
    }

    private PublicMenuSectionResponse toSectionResponse(MenuSection section, List<MenuItem> items) {
        return PublicMenuSectionResponse.builder()
                .id(section.getId())
                .name(section.getName())
                .description(section.getDescription())
                .displayOrder(section.getDisplayOrder())
                .items(items == null ? null : items.stream().map(this::toItemResponse).toList())
                .build();
    }

    private PublicMenuItemResponse toItemResponse(MenuItem item) {
        return PublicMenuItemResponse.builder()
                .id(item.getId())
                .sku(item.getSku())
                .name(item.getName())
                .description(item.getDescription())
                .basePrice(item.getBasePrice())
                .imageUrl(item.getImageUrl())
                .displayOrder(item.getDisplayOrder())
                .build();
    }
}
