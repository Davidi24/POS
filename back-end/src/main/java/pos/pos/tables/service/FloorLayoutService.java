package pos.pos.tables.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.tables.FloorLayoutNotFoundException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.storage.FloorPlanImageStorage;
import pos.pos.tables.dto.FloorLayoutRequest;
import pos.pos.tables.dto.FloorLayoutResponse;
import pos.pos.tables.entity.FloorLayout;
import pos.pos.tables.mapper.FloorLayoutMapper;
import pos.pos.tables.repository.FloorLayoutRepository;
import pos.pos.utils.NormalizationUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FloorLayoutService {

    private final RestaurantScopeService restaurantScopeService;
    private final FloorLayoutRepository floorLayoutRepository;
    private final FloorLayoutMapper floorLayoutMapper;
    private final FloorPlanImageStorage imageStorage;

    @Transactional(readOnly = true)
    public List<FloorLayoutResponse> getFloorLayouts(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId
    ) {
        restaurantScopeService.requireAccessibleBranch(
                authentication,
                restaurantId,
                branchId
        );

        return floorLayoutRepository
                .findAllByBranch_IdOrderByFloorNameAsc(branchId)
                .stream()
                .map(floorLayoutMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FloorLayoutResponse getFloorLayout(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID floorLayoutId
    ) {
        restaurantScopeService.requireAccessibleBranch(
                authentication,
                restaurantId,
                branchId
        );

        return floorLayoutMapper.toResponse(
                requireFloorLayout(branchId, floorLayoutId)
        );
    }

    @Transactional
    public FloorLayoutResponse createFloorLayout(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            FloorLayoutRequest request
    ) {
        Branch branch = restaurantScopeService.requireManageableBranch(
                authentication,
                restaurantId,
                branchId
        );

        String floorName = normalizeFloorName(request);

        if (
                floorLayoutRepository.existsByBranch_IdAndFloorName(
                        branchId,
                        floorName
                )
        ) {
            throw new AuthException(
                    "A layout already exists for this floor",
                    HttpStatus.CONFLICT
            );
        }

        UUID actorId = restaurantScopeService.currentUserId(authentication);

        FloorLayout floorLayout = new FloorLayout();
        floorLayout.setRestaurant(branch.getRestaurant());
        floorLayout.setBranch(branch);
        floorLayout.setCreatedBy(actorId);
        floorLayout.setUpdatedBy(actorId);

        floorLayoutMapper.applyRequest(floorLayout, request);

        return floorLayoutMapper.toResponse(
                floorLayoutRepository.saveAndFlush(floorLayout)
        );
    }

    @Transactional
    public FloorLayoutResponse updateFloorLayout(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID floorLayoutId,
            FloorLayoutRequest request
    ) {
        restaurantScopeService.requireManageableBranch(
                authentication,
                restaurantId,
                branchId
        );

        FloorLayout floorLayout = requireFloorLayout(
                branchId,
                floorLayoutId
        );

        String floorName = normalizeFloorName(request);

        floorLayoutRepository
                .findByBranch_IdAndFloorName(branchId, floorName)
                .filter(existing ->
                        !Objects.equals(
                                existing.getId(),
                                floorLayout.getId()
                        )
                )
                .ifPresent(existing -> {
                    throw new AuthException(
                            "A layout already exists for this floor",
                            HttpStatus.CONFLICT
                    );
                });

        floorLayoutMapper.applyRequest(floorLayout, request);
        floorLayout.setUpdatedBy(
                restaurantScopeService.currentUserId(authentication)
        );

        return floorLayoutMapper.toResponse(
                floorLayoutRepository.saveAndFlush(floorLayout)
        );
    }

    @Transactional
    public FloorLayoutResponse uploadPlanImage(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID floorLayoutId,
            MultipartFile file
    ) {
        restaurantScopeService.requireManageableBranch(
                authentication,
                restaurantId,
                branchId
        );

        FloorLayout floorLayout = requireFloorLayout(
                branchId,
                floorLayoutId
        );

        String previousImageKey = floorLayout.getPlanImageKey();

        FloorPlanImageStorage.StoredImage storedImage =
                imageStorage.store(restaurantId, branchId, file);

        floorLayout.setPlanImageKey(storedImage.objectKey());
        floorLayout.setUpdatedBy(
                restaurantScopeService.currentUserId(authentication)
        );

        FloorLayout saved =
                floorLayoutRepository.saveAndFlush(floorLayout);

        deleteStoredImage(
                restaurantId,
                branchId,
                previousImageKey
        );

        return floorLayoutMapper.toResponse(saved);
    }

    @Transactional
    public FloorLayoutResponse removePlanImage(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID floorLayoutId
    ) {
        restaurantScopeService.requireManageableBranch(
                authentication,
                restaurantId,
                branchId
        );

        FloorLayout floorLayout = requireFloorLayout(
                branchId,
                floorLayoutId
        );

        String previousImageKey = floorLayout.getPlanImageKey();

        floorLayout.setPlanImageKey(null);
        floorLayout.setUpdatedBy(
                restaurantScopeService.currentUserId(authentication)
        );

        FloorLayout saved =
                floorLayoutRepository.saveAndFlush(floorLayout);

        deleteStoredImage(
                restaurantId,
                branchId,
                previousImageKey
        );

        return floorLayoutMapper.toResponse(saved);
    }

    @Transactional
    public void deleteFloorLayout(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UUID floorLayoutId
    ) {
        restaurantScopeService.requireManageableBranch(
                authentication,
                restaurantId,
                branchId
        );

        FloorLayout floorLayout = requireFloorLayout(
                branchId,
                floorLayoutId
        );

        String imageKey = floorLayout.getPlanImageKey();

        floorLayoutRepository.delete(floorLayout);
        floorLayoutRepository.flush();

        deleteStoredImage(
                restaurantId,
                branchId,
                imageKey
        );
    }

    private FloorLayout requireFloorLayout(
            UUID branchId,
            UUID floorLayoutId
    ) {
        return floorLayoutRepository
                .findByIdAndBranch_Id(floorLayoutId, branchId)
                .orElseThrow(FloorLayoutNotFoundException::new);
    }

    private String normalizeFloorName(
            FloorLayoutRequest request
    ) {
        String normalized =
                NormalizationUtils.normalize(request.getFloorName());

        request.setFloorName(normalized);

        return normalized;
    }

    private void deleteStoredImage(
            UUID restaurantId,
            UUID branchId,
            String objectKey
    ) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        int separator = objectKey.lastIndexOf('/');

        String fileName = separator >= 0
                ? objectKey.substring(separator + 1)
                : objectKey;

        imageStorage.delete(
                restaurantId,
                branchId,
                fileName
        );
    }
}