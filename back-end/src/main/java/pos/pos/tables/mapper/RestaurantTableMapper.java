package pos.pos.tables.mapper;

import org.springframework.stereotype.Component;
import pos.pos.tables.dto.TableAvailabilityResponse;
import pos.pos.tables.dto.TableLayoutItemResponse;
import pos.pos.tables.dto.TableRequest;
import pos.pos.tables.dto.TableResponse;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.tables.entity.TableCategory;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;

import java.util.List;
import java.util.UUID;

@Component
public class RestaurantTableMapper {

    public void applyRequest(
            RestaurantTable table,
            TableRequest request,
            TableCategory category
    ) {
        table.setCategory(category);
        table.setTableNumber(request.getTableNumber());
        table.setName(request.getName());
        table.setCapacity(request.getCapacity());
        table.setFloor(request.getFloor());
        table.setPositionX(request.getPositionX());
        table.setPositionY(request.getPositionY());
        table.setShape(request.getShape() == null ? TableShape.RECTANGLE : request.getShape());
        table.setStatus(request.getStatus() == null ? TableStatus.AVAILABLE : request.getStatus());
        table.setActive(request.getActive() == null || request.getActive());
        table.setQrCodeValue(request.getQrCodeValue());
    }

    public TableResponse toResponse(RestaurantTable table, List<UUID> mergedTableIds, int effectiveCapacity) {
        if (table == null) {
            return null;
        }

        return TableResponse.builder()
                .id(table.getId())
                .restaurantId(table.getRestaurant() == null ? null : table.getRestaurant().getId())
                .branchId(table.getBranch() == null ? null : table.getBranch().getId())
                .categoryId(table.getCategory() == null ? null : table.getCategory().getId())
                .categoryCode(table.getCategory() == null ? null : table.getCategory().getCode())
                .categoryName(table.getCategory() == null ? null : table.getCategory().getName())
                .mergedIntoTableId(table.getMergedInto() == null ? null : table.getMergedInto().getId())
                .mergedTableIds(mergedTableIds)
                .tableNumber(table.getTableNumber())
                .name(table.getName())
                .capacity(table.getCapacity())
                .effectiveCapacity(effectiveCapacity)
                .floor(table.getFloor())
                .positionX(table.getPositionX())
                .positionY(table.getPositionY())
                .shape(table.getShape())
                .status(table.getStatus())
                .active(table.isActive())
                .qrCodeValue(table.getQrCodeValue())
                .createdAt(table.getCreatedAt())
                .updatedAt(table.getUpdatedAt())
                .build();
    }

    public TableLayoutItemResponse toLayoutItemResponse(RestaurantTable table, List<UUID> mergedTableIds, int effectiveCapacity) {
        if (table == null) {
            return null;
        }

        return TableLayoutItemResponse.builder()
                .tableId(table.getId())
                .mergedIntoTableId(table.getMergedInto() == null ? null : table.getMergedInto().getId())
                .mergedTableIds(mergedTableIds)
                .tableNumber(table.getTableNumber())
                .name(table.getName())
                .capacity(table.getCapacity())
                .effectiveCapacity(effectiveCapacity)
                .floor(table.getFloor())
                .positionX(table.getPositionX())
                .positionY(table.getPositionY())
                .shape(table.getShape())
                .status(table.getStatus())
                .active(table.isActive())
                .build();
    }

    public TableAvailabilityResponse toAvailabilityResponse(
            RestaurantTable table,
            List<UUID> mergedTableIds,
            int effectiveCapacity,
            boolean operationallyAvailable,
            boolean availableForRequestedWindow,
            String blockingReason,
            List<UUID> overlappingReservationIds
    ) {
        if (table == null) {
            return null;
        }

        return TableAvailabilityResponse.builder()
                .tableId(table.getId())
                .tableNumber(table.getTableNumber())
                .name(table.getName())
                .floor(table.getFloor())
                .capacity(table.getCapacity())
                .effectiveCapacity(effectiveCapacity)
                .status(table.getStatus())
                .active(table.isActive())
                .mergedIntoTableId(table.getMergedInto() == null ? null : table.getMergedInto().getId())
                .mergedTableIds(mergedTableIds)
                .operationallyAvailable(operationallyAvailable)
                .availableForRequestedWindow(availableForRequestedWindow)
                .blockingReason(blockingReason)
                .overlappingReservationIds(overlappingReservationIds)
                .build();
    }
}
