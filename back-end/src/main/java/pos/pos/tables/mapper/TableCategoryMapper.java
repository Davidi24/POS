package pos.pos.tables.mapper;

import org.springframework.stereotype.Component;
import pos.pos.tables.dto.TableCategoryRequest;
import pos.pos.tables.dto.TableCategoryResponse;
import pos.pos.tables.entity.TableCategory;
import pos.pos.tables.enums.TableLocationType;

@Component
public class TableCategoryMapper {

    public TableCategoryResponse toResponse(TableCategory tableCategory) {
        if (tableCategory == null) {
            return null;
        }

        return TableCategoryResponse.builder()
                .id(tableCategory.getId())
                .restaurantId(tableCategory.getBranch() == null || tableCategory.getBranch().getRestaurant() == null
                        ? null
                        : tableCategory.getBranch().getRestaurant().getId())
                .branchId(tableCategory.getBranch() == null ? null : tableCategory.getBranch().getId())
                .code(tableCategory.getCode())
                .name(tableCategory.getName())
                .description(tableCategory.getDescription())
                .defaultCapacity(tableCategory.getDefaultCapacity())
                .locationType(tableCategory.getLocationType())
                .color(tableCategory.getColor())
                .displayOrder(tableCategory.getDisplayOrder())
                .active(tableCategory.isActive())
                .createdAt(tableCategory.getCreatedAt())
                .updatedAt(tableCategory.getUpdatedAt())
                .build();
    }

    public void applyRequest(
            TableCategory tableCategory,
            TableCategoryRequest request
    ) {
        tableCategory.setCode(request.getCode());
        tableCategory.setName(request.getName());
        tableCategory.setDescription(request.getDescription());
        tableCategory.setDefaultCapacity(request.getDefaultCapacity());
        tableCategory.setLocationType(request.getLocationType() == null ? TableLocationType.INDOOR : request.getLocationType());
        tableCategory.setColor(request.getColor());
        tableCategory.setDisplayOrder(request.getDisplayOrder());
        tableCategory.setActive(request.getActive() == null || request.getActive());
    }
}
