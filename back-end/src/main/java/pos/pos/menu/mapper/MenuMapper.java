package pos.pos.menu.mapper;

import org.springframework.stereotype.Component;
import pos.pos.menu.dto.MenuItemSummaryResponse;
import pos.pos.menu.dto.MenuItemOptionGroupSummaryResponse;
import pos.pos.menu.dto.MenuResponse;
import pos.pos.menu.dto.OptionGroupTypeResponse;
import pos.pos.menu.dto.MenuRestaurantSummaryResponse;
import pos.pos.menu.dto.MenuSectionSummaryResponse;
import pos.pos.menu.dto.MenuVariantSummaryResponse;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.entity.MenuItem;
import pos.pos.menu.entity.MenuItemOptionGroup;
import pos.pos.menu.entity.MenuSection;
import pos.pos.menu.entity.MenuVariant;
import pos.pos.menu.entity.OptionGroupType;

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
                .createdBy(menu.getCreatedBy() == null ? null : menu.getCreatedBy().getId())
                .updatedBy(menu.getUpdatedBy() == null ? null : menu.getUpdatedBy().getId())
                .createdAt(menu.getCreatedAt())
                .updatedAt(menu.getUpdatedAt())
                .sections(sections == null ? null : sections.stream()
                        .map(section -> toMenuSectionResponse(section, itemsBySectionId.get(section.getId())))
                        .toList())
                .build();
    }

    public MenuSectionSummaryResponse toMenuSectionResponse(MenuSection section) {
        return toMenuSectionResponse(section, null);
    }

    public MenuSectionSummaryResponse toMenuSectionResponse(MenuSection section, List<MenuItem> items) {
        if (section == null) {
            return null;
        }

        return MenuSectionSummaryResponse.builder()
                .id(section.getId())
                .name(section.getName())
                .description(section.getDescription())
                .active(section.isActive())
                .displayOrder(section.getDisplayOrder())
                .items(items == null ? null : items.stream().map(this::toItemSummaryResponse).toList())
                .build();
    }

    public MenuItemSummaryResponse toMenuItemResponse(MenuItem item) {
        return toMenuItemResponse(item, null, null);
    }

    public MenuItemSummaryResponse toMenuItemResponse(
            MenuItem item,
            List<MenuVariant> variants,
            List<MenuItemOptionGroup> optionGroups
    ) {
        if (item == null) {
            return null;
        }

        return MenuItemSummaryResponse.builder()
                .id(item.getId())
                .sku(item.getSku())
                .name(item.getName())
                .description(item.getDescription())
                .basePrice(item.getBasePrice())
                .imageUrl(item.getImageUrl())
                .available(item.isAvailable())
                .displayOrder(item.getDisplayOrder())
                .variants(variants == null ? null : variants.stream().map(this::toMenuVariantSummaryResponse).toList())
                .optionGroups(optionGroups == null ? null : optionGroups.stream().map(this::toMenuItemOptionGroupSummaryResponse).toList())
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

    public MenuVariantSummaryResponse toMenuVariantSummaryResponse(MenuVariant variant) {
        return MenuVariantSummaryResponse.builder()
                .id(variant.getId())
                .name(variant.getName())
                .sku(variant.getSku())
                .priceDelta(variant.getPriceDelta())
                .isDefault(variant.isDefault())
                .active(variant.isActive())
                .displayOrder(variant.getDisplayOrder())
                .build();
    }

    public OptionGroupTypeResponse toOptionGroupTypeResponse(OptionGroupType type) {
        if (type == null) {
            return null;
        }

        return OptionGroupTypeResponse.builder()
                .id(type.getId())
                .code(type.getCode())
                .name(type.getName())
                .description(type.getDescription())
                .build();
    }

    private MenuItemOptionGroupSummaryResponse toMenuItemOptionGroupSummaryResponse(MenuItemOptionGroup link) {
        return MenuItemOptionGroupSummaryResponse.builder()
                .linkId(link.getId())
                .optionGroupId(link.getOptionGroup().getId())
                .name(link.getOptionGroup().getName())
                .description(link.getOptionGroup().getDescription())
                .active(link.getOptionGroup().isActive())
                .displayOrder(link.getDisplayOrder())
                .minSelect(link.getOptionGroup().getMinSelect())
                .maxSelect(link.getOptionGroup().getMaxSelect())
                .required(link.getOptionGroup().isRequired())
                .minSelectOverride(link.getMinSelectOverride())
                .maxSelectOverride(link.getMaxSelectOverride())
                .requiredOverride(link.getRequiredOverride())
                .build();
    }

    private MenuItemSummaryResponse toItemSummaryResponse(MenuItem item) {
        return toMenuItemResponse(item);
    }
}
