package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.dto.BranchResponse;
import pos.pos.restaurant.dto.CreateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchStatusRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.mapper.BranchMapper;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;
    private final RestaurantScopeService restaurantScopeService;
    private final BranchValidationService branchValidationService;

    public PageResponse<BranchResponse> getBranches(
            Authentication authentication,
            UUID restaurantId,
            String search,
            Boolean active,
            String status,
            UUID managerUserId,
            Integer page,
            Integer size,
            String sortBy,
            String direction
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);

        Pageable pageable = PageRequest.of(
                page == null ? 0 : page,
                size == null ? DEFAULT_PAGE_SIZE : size,
                Sort.by(resolveDirection(direction), resolveSortProperty(sortBy))
        );

        String normalizedSearch = NormalizationUtils.normalizeLower(search);
        String searchLike = normalizedSearch == null ? null : "%" + normalizedSearch + "%";
        BranchStatus normalizedStatus = resolveStatus(status);

        var branchesPage = branchRepository.searchRestaurantBranches(
                restaurantId,
                active,
                normalizedStatus,
                managerUserId,
                searchLike,
                pageable
        );

        var items = branchesPage.getContent().stream()
                .map(branchMapper::toResponse)
                .toList();

        return PageResponse.from(new PageImpl<>(items, pageable, branchesPage.getTotalElements()));
    }

    @Transactional
    public BranchResponse createBranch(Authentication authentication, UUID restaurantId, CreateBranchRequest request) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        String normalizedCode = branchValidationService.normalizeAndValidateFields(
                request.getCode(),
                request.getName(),
                restaurantId,
                null
        ).code();
        UUID managerUserId = branchValidationService.validateManagerUser(request.getManagerUserId(), restaurantId);

        Branch branch = branchMapper.toNewEntity(restaurant, request, normalizedCode, managerUserId, actorId);
        branchRepository.save(branch);
        return branchMapper.toResponse(branch);
    }

    public BranchResponse getBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        Branch branch = restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return branchMapper.toResponse(branch);
    }

    @Transactional
    public BranchResponse updateBranch(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpdateBranchRequest request
    ) {
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        String normalizedCode = branchValidationService.normalizeAndValidateFields(
                request.getCode(),
                request.getName(),
                restaurantId,
                branchId
        ).code();
        UUID managerUserId = branchValidationService.validateManagerUser(request.getManagerUserId(), restaurantId);
        branchValidationService.validateStatusConsistency(request.getIsActive(), request.getStatus());

        branchMapper.updateEntity(branch, request, normalizedCode, managerUserId, actorId);
        branchRepository.save(branch);
        return branchMapper.toResponse(branch);
    }

    @Transactional
    public BranchResponse updateBranchStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpdateBranchStatusRequest request
    ) {
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        branchValidationService.validateStatusConsistency(request.getIsActive(), request.getStatus());
        branchMapper.updateStatus(
                branch,
                request.getIsActive(),
                request.getStatus(),
                restaurantScopeService.currentUserId(authentication)
        );
        branchRepository.save(branch);
        return branchMapper.toResponse(branch);
    }

    @Transactional
    public void deleteBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        branchMapper.markDeleted(
                branch,
                restaurantScopeService.currentUserId(authentication),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        branchRepository.save(branch);
    }

    private String resolveSortProperty(String sortBy) {
        String normalized = NormalizationUtils.normalizeLower(sortBy);
        if (normalized == null || normalized.equals("createdat") || normalized.equals("created_at")) {
            return "createdAt";
        }

        return switch (normalized) {
            case "updatedat", "updated_at" -> "updatedAt";
            case "name" -> "name";
            case "code" -> "code";
            case "status" -> "status";
            default -> throw new AuthException("Invalid sortBy value", HttpStatus.BAD_REQUEST);
        };
    }

    private Sort.Direction resolveDirection(String direction) {
        try {
            return Sort.Direction.fromString(
                    NormalizationUtils.normalize(direction) == null ? "desc" : direction
            );
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid sort direction", HttpStatus.BAD_REQUEST);
        }
    }

    private BranchStatus resolveStatus(String status) {
        String normalized = NormalizationUtils.normalizeUpper(status);
        if (normalized == null) {
            return null;
        }

        try {
            return BranchStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new AuthException("Invalid status value", HttpStatus.BAD_REQUEST);
        }
    }
}
