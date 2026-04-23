package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.BranchCodeAlreadyExistsException;
import pos.pos.exception.restaurant.BranchManagerNotFoundException;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.util.BranchFieldNormalizer;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BranchValidationService {

    private final BranchRepository branchRepository;
    private final UserRepository userRepository;

    public NormalizedBranchFields normalizeAndValidateFields(
            String code,
            String fallbackName,
            UUID restaurantId,
            UUID branchId
    ) {
        String normalizedCode = BranchFieldNormalizer.normalizeCodeOrFallback(code, fallbackName);
        if (normalizedCode == null) {
            throw new AuthException("code is required", HttpStatus.BAD_REQUEST);
        }

        assertUniqueCode(restaurantId, normalizedCode, branchId);
        return new NormalizedBranchFields(normalizedCode);
    }

    public void validateStatusConsistency(Boolean isActive, BranchStatus status) {
        if (Boolean.TRUE.equals(isActive) && status != BranchStatus.ACTIVE) {
            throw new AuthException("Non-active branch statuses must have isActive=false", HttpStatus.BAD_REQUEST);
        }

        if (Boolean.FALSE.equals(isActive) && status == BranchStatus.ACTIVE) {
            throw new AuthException("ACTIVE branches must have isActive=true", HttpStatus.BAD_REQUEST);
        }
    }

    public UUID validateManagerUser(UUID managerUserId, UUID restaurantId) {
        if (managerUserId == null) {
            return null;
        }

        User manager = userRepository.findByIdAndDeletedAtIsNull(managerUserId)
                .orElseThrow(BranchManagerNotFoundException::new);

        if (!manager.isActive()) {
            throw new AuthException("Manager user must be active", HttpStatus.BAD_REQUEST);
        }

        if (!Objects.equals(manager.getRestaurantId(), restaurantId)) {
            throw new AuthException("Manager user must belong to the same restaurant", HttpStatus.BAD_REQUEST);
        }

        return manager.getId();
    }

    private void assertUniqueCode(UUID restaurantId, String code, UUID branchId) {
        boolean exists = branchId == null
                ? branchRepository.existsByRestaurantIdAndCodeAndDeletedAtIsNull(restaurantId, code)
                : branchRepository.existsByRestaurantIdAndCodeAndIdNotAndDeletedAtIsNull(restaurantId, code, branchId);

        if (exists) {
            throw new BranchCodeAlreadyExistsException();
        }
    }

    public record NormalizedBranchFields(String code) {
    }
}
