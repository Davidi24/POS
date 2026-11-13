package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.dto.BranchRemovalCheckResponse;
import pos.pos.restaurant.dto.BranchResponse;
import pos.pos.restaurant.dto.CreateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchRequest;
import pos.pos.restaurant.dto.UpdateBranchStatusRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.mapper.BranchMapper;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantUsageRepository;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.utils.NormalizationUtils;
import pos.pos.utils.PageableUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchAdminService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;
    private final RestaurantScopeService restaurantScopeService;
    private final ActorScopeService actorScopeService;
    private final BranchValidationService branchValidationService;
    private final RestaurantUsageRepository restaurantUsageRepository;
    private final RestaurantClosureGuard closureGuard;

    private static final String BRANCHES_ONLY_SUPER_ADMIN = "Only the Super Admin can add, close or remove branches";

    @Transactional(readOnly = true)
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

        Pageable pageable = PageableUtils.create(page, size, direction, resolveSortProperty(sortBy), DEFAULT_PAGE_SIZE);
        String searchLike = NormalizationUtils.normalizeLowerLike(search);
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
        Restaurant restaurant = restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, BRANCHES_ONLY_SUPER_ADMIN);
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

    @Transactional(readOnly = true)
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
        ActorScope scope = actorScopeService.resolve(authentication);
        Branch branch = restaurantScopeService.requireManageableBranch(scope, restaurantId, branchId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        String normalizedCode = branchValidationService.normalizeAndValidateFields(
                request.getCode(),
                request.getName(),
                restaurantId,
                branchId
        ).code();
        // Owners keep a branch's phone, email, description and manager up to date; its name, code and whether it's
        // open are for super admins.
        if (!scope.superAdmin()) {
            List<String> changed = new ArrayList<>();
            if (!Objects.equals(NormalizationUtils.normalize(request.getName()), branch.getName())) changed.add("name");
            if (!Objects.equals(normalizedCode, branch.getCode())) changed.add("code");
            if (request.getStatus() != branch.getStatus() || !Objects.equals(request.getIsActive(), branch.isActive())) changed.add("status");
            if (!changed.isEmpty()) {
                throw new AuthException("Only the Super Admin can change a branch's " + String.join(", ", changed), HttpStatus.FORBIDDEN);
            }
        }
        UUID managerUserId = branchValidationService.validateManagerUser(request.getManagerUserId(), restaurantId);
        branchValidationService.validateBranchStatusTransition(branch.getStatus(), request.getStatus());
        branchValidationService.validateStatusConsistency(request.getIsActive(), request.getStatus());
        closureGuard.branch(branch,Boolean.TRUE.equals(request.getIsActive()));

        if (request.getExpectedVersion() == null || request.getExpectedVersion() != branch.getVersion()) {
            throw new AuthException("These details have changed, or the edit version is missing. Refresh and review your changes before saving.", HttpStatus.CONFLICT);
        }
        branchMapper.updateEntity(branch, request, normalizedCode, managerUserId, actorId);
        branchRepository.saveAndFlush(branch);
        return branchMapper.toResponse(branch);
    }

    @Transactional
    public BranchResponse updateBranchStatus(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            UpdateBranchStatusRequest request
    ) {
        restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, BRANCHES_ONLY_SUPER_ADMIN);
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        branchValidationService.validateBranchStatusTransition(branch.getStatus(), request.getStatus());
        branchValidationService.validateStatusConsistency(request.getIsActive(), request.getStatus());
        closureGuard.branch(branch,Boolean.TRUE.equals(request.getIsActive()));
        branchMapper.updateStatus(
                branch,
                request.getIsActive(),
                request.getStatus(),
                restaurantScopeService.currentUserId(authentication)
        );
        branchRepository.save(branch);
        return branchMapper.toResponse(branch);
    }

    /** What still uses a branch; it can only be removed when nothing does and it isn't the restaurant's last one. */
    @Transactional(readOnly = true)
    public BranchRemovalCheckResponse removalCheck(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        return removalCheck(restaurantScopeService.requireExistingBranch(restaurantId,branchId));
    }

    private BranchRemovalCheckResponse removalCheck(Branch branch) {
        UUID restaurantId=branch.getRestaurant().getId(),branchId=branch.getId();
        RestaurantUsageRepository.BranchUsage usage = restaurantUsageRepository.branchUsage(restaurantId, branchId, OffsetDateTime.now(ZoneOffset.UTC));
        boolean lastBranch = branchRepository.countByRestaurantIdAndDeletedAtIsNull(restaurantId) <= 1;
        List<String> reasons = new ArrayList<>(closureGuard.removalReasons(branch));
        if (lastBranch) reasons.add("It's the restaurant's only branch");
        if (usage.openOrders() > 0) reasons.add(plural(usage.openOrders(), "open order", "open orders"));
        if (usage.upcomingBookings() > 0) reasons.add(plural(usage.upcomingBookings(), "booking still to come", "bookings still to come"));
        if (usage.openShifts() > 0) reasons.add(plural(usage.openShifts(), "person clocked in", "people clocked in"));
        if (usage.staff() > 0) reasons.add(plural(usage.staff(), "staff member works here", "staff members work here"));
        if (usage.devices() > 0) reasons.add(plural(usage.devices(), "device is set up here", "devices are set up here"));
        return new BranchRemovalCheckResponse(reasons.isEmpty(), usage.openOrders(), usage.upcomingBookings(), usage.openShifts(),
                usage.staff(), usage.devices(), lastBranch, reasons);
    }

    private static String plural(long count, String one, String many) {
        return count + " " + (count == 1 ? one : many);
    }

    @Transactional
    public void deleteBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        restaurantScopeService.requirePlatformManagedRestaurant(authentication, restaurantId, BRANCHES_ONLY_SUPER_ADMIN);
        Branch branch = restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        BranchRemovalCheckResponse check = removalCheck(branch);
        if (!check.canRemove()) {
            throw new AuthException("This branch is still in use: " + String.join(", ", check.reasons())
                    + ". Move or finish these first, or close the branch instead.", HttpStatus.CONFLICT);
        }
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
