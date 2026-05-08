package pos.pos.menu.mapper;

import org.springframework.stereotype.Component;
import pos.pos.menu.dto.MenuItemSummaryResponse;
import pos.pos.menu.dto.MenuResponse;
import pos.pos.menu.dto.MenuRestaurantSummaryResponse;
import pos.pos.menu.dto.MenuSectionSummaryResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuSection;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MenuMapper {

    public MenuResponse toMenuResponse(Menu menu) {
        return toMenuResponse(menu, null, Map.of());
    }

    public MenuResponse toMenuResponse(
            Menu menu,
            List<MenuSection> sections,
            Map<UUID, List<MenuItem>> itemsBySectionId
    ) {
        if (menu == null) {
            return null;
        }

        return MenuResponse.builder()
                .id(menu.getId())
                .restaurant(toRestaurantSummary(menu))
                .code(menu.getCode())
                .name(menu.getName())
                .description(menu.getDescription())
                .active(menu.isActive())
                .displayOrder(menu.getDisplayOrder())
                .createdBy(menu.getCreatedBy())
                .updatedBy(menu.getUpdatedBy())
                .createdAt(menu.getCreatedAt())
                .updatedAt(menu.getUpdatedAt())
                .sections(sections == null ? null : sections.stream()
                        .map(section -> toSectionSummaryResponse(section, itemsBySectionId.get(section.getId())))
                        .toList())
                .build();
    }

    private MenuRestaurantSummaryResponse toRestaurantSummary(Menu menu) {
        if (menu.getRestaurant() == null) {
            return null;
        }

        return MenuRestaurantSummaryResponse.builder()
                .id(menu.getRestaurant().getId())
                .code(menu.getRestaurant().getCode())
                .name(menu.getRestaurant().getName())
                .build();
    }

    private MenuSectionSummaryResponse toSectionSummaryResponse(MenuSection section, List<MenuItem> items) {
        return MenuSectionSummaryResponse.builder()
                .id(section.getId())
                .name(section.getName())
                .description(section.getDescription())
                .active(section.isActive())
                .displayOrder(section.getDisplayOrder())
                .items(items == null ? null : items.stream().map(this::toItemSummaryResponse).toList())
                .build();
    }

    private MenuItemSummaryResponse toItemSummaryResponse(MenuItem item) {
        return MenuItemSummaryResponse.builder()
                .id(item.getId())
                .sku(item.getSku())
                .name(item.getName())
                .description(item.getDescription())
                .basePrice(item.getBasePrice())
                .imageUrl(item.getImageUrl())
                .available(item.isAvailable())
                .displayOrder(item.getDisplayOrder())
                .build();
    }
}
