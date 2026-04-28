package pos.pos.restaurant.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.BranchResponse;
import pos.pos.restaurant.dto.CreateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class BranchMapper {

    public BranchResponse toResponse(Branch branch) {
        if (branch == null) {
            return null;
        }

        return BranchResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .code(branch.getCode())
                .description(branch.getDescription())
                .email(branch.getEmail())
                .phone(branch.getPhone())
                .isActive(branch.isActive())
                .status(branch.getStatus())
                .managerUserId(branch.getManagerUserId())
                .createdAt(branch.getCreatedAt())
                .updatedAt(branch.getUpdatedAt())
                .build();
    }

    public Branch toNewEntity(
            Restaurant restaurant,
            CreateBranchRequest request,
            String normalizedCode,
            UUID managerUserId,
            UUID actorId
    ) {
        Branch branch = new Branch();
        branch.setRestaurant(restaurant);
        applyBaseFields(
                branch,
                request.getName(),
                normalizedCode,
                request.getDescription(),
                request.getEmail(),
                request.getPhone(),
                managerUserId
        );
        branch.setActive(true);
        branch.setStatus(BranchStatus.ACTIVE);
        branch.setCreatedBy(actorId);
        branch.setUpdatedBy(actorId);
        return branch;
    }

    public void updateEntity(
            Branch branch,
            UpdateBranchRequest request,
            String normalizedCode,
            UUID managerUserId,
            UUID actorId
    ) {
        applyBaseFields(
                branch,
                request.getName(),
                normalizedCode,
                request.getDescription(),
                request.getEmail(),
                request.getPhone(),
                managerUserId
        );
        branch.setActive(request.getIsActive());
        branch.setStatus(request.getStatus());
        branch.setUpdatedBy(actorId);
    }

    public void updateStatus(Branch branch, boolean isActive, BranchStatus status, UUID actorId) {
        branch.setActive(isActive);
        branch.setStatus(status);
        branch.setUpdatedBy(actorId);
    }

    public void markDeleted(Branch branch, UUID actorId, OffsetDateTime deletedAt) {
        updateStatus(branch, false, BranchStatus.ARCHIVED, actorId);
        branch.setDeletedAt(deletedAt);
    }

    private void applyBaseFields(
            Branch branch,
            String name,
            String code,
            String description,
            String email,
            String phone,
            UUID managerUserId
    ) {
        branch.setName(name);
        branch.setCode(code);
        branch.setDescription(description);
        branch.setEmail(email);
        branch.setPhone(phone);
        branch.setManagerUserId(managerUserId);
    }
}
