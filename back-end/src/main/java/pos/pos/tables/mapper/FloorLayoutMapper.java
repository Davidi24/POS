package pos.pos.tables.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.storage.FloorPlanImageStorage;
import pos.pos.tables.dto.FloorLayoutRequest;
import pos.pos.tables.dto.FloorLayoutResponse;
import pos.pos.tables.entity.FloorLayout;

@Component
@RequiredArgsConstructor
public class FloorLayoutMapper {

    private final FloorPlanImageStorage imageStorage;

    public void applyRequest(
            FloorLayout floorLayout,
            FloorLayoutRequest request
    ) {
        floorLayout.setFloorName(request.getFloorName());

        if (request.getPlanOffsetX() != null) {
            floorLayout.setPlanOffsetX(request.getPlanOffsetX());
        }

        if (request.getPlanOffsetY() != null) {
            floorLayout.setPlanOffsetY(request.getPlanOffsetY());
        }

        if (request.getPlanScale() != null) {
            floorLayout.setPlanScale(request.getPlanScale());
        }
    }

    public FloorLayoutResponse toResponse(FloorLayout floorLayout) {
        String imageKey = floorLayout.getPlanImageKey();

        return FloorLayoutResponse.builder()
                .id(floorLayout.getId())
                .restaurantId(floorLayout.getRestaurant().getId())
                .branchId(floorLayout.getBranch().getId())
                .floorName(floorLayout.getFloorName())
                .planImageKey(imageKey)
                .planImageUrl(imageStorage.publicUrl(imageKey))
                .planOffsetX(floorLayout.getPlanOffsetX())
                .planOffsetY(floorLayout.getPlanOffsetY())
                .planScale(floorLayout.getPlanScale())
                .createdAt(floorLayout.getCreatedAt())
                .updatedAt(floorLayout.getUpdatedAt())
                .build();
    }
}