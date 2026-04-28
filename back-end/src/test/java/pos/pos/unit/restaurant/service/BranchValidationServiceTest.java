package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.BranchCodeAlreadyExistsException;
import pos.pos.exception.restaurant.BranchManagerNotFoundException;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.service.BranchValidationService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("BranchValidationService")
class BranchValidationServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OTHER_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000040");

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BranchValidationService branchValidationService;

    @Test
    @DisplayName("normalizeAndValidateFields should return normalized code when unique on create")
    void shouldNormalizeAndAcceptUniqueCodeOnCreate() {
        given(branchRepository.existsByRestaurantIdAndCodeAndDeletedAtIsNull(RESTAURANT_ID, "DOWN_TOWN"))
                .willReturn(false);

        BranchValidationService.NormalizedBranchFields result =
                branchValidationService.normalizeAndValidateFields("down-town", "Downtown", RESTAURANT_ID, null);

        assertThat(result.code()).isEqualTo("DOWN_TOWN");
    }

    @Test
    @DisplayName("normalizeAndValidateFields should fall back to name when code is blank")
    void shouldFallbackToNameWhenCodeBlank() {
        given(branchRepository.existsByRestaurantIdAndCodeAndDeletedAtIsNull(RESTAURANT_ID, "DOWNTOWN"))
                .willReturn(false);

        BranchValidationService.NormalizedBranchFields result =
                branchValidationService.normalizeAndValidateFields("  ", "Downtown", RESTAURANT_ID, null);

        assertThat(result.code()).isEqualTo("DOWNTOWN");
    }

    @Test
    @DisplayName("normalizeAndValidateFields should reject duplicate code on create")
    void shouldRejectDuplicateCodeOnCreate() {
        given(branchRepository.existsByRestaurantIdAndCodeAndDeletedAtIsNull(RESTAURANT_ID, "DOWNTOWN"))
                .willReturn(true);

        assertThatThrownBy(() ->
                branchValidationService.normalizeAndValidateFields("downtown", "Downtown", RESTAURANT_ID, null))
                .isInstanceOf(BranchCodeAlreadyExistsException.class);
    }

    @Test
    @DisplayName("normalizeAndValidateFields should skip self-conflict on update")
    void shouldSkipSelfConflictOnUpdate() {
        given(branchRepository.existsByRestaurantIdAndCodeAndIdNotAndDeletedAtIsNull(RESTAURANT_ID, "DOWNTOWN", BRANCH_ID))
                .willReturn(false);

        BranchValidationService.NormalizedBranchFields result =
                branchValidationService.normalizeAndValidateFields("downtown", "Downtown", RESTAURANT_ID, BRANCH_ID);

        assertThat(result.code()).isEqualTo("DOWNTOWN");
    }

    @Test
    @DisplayName("normalizeAndValidateFields should reject a code used by another branch on update")
    void shouldRejectConflictingCodeOnUpdate() {
        given(branchRepository.existsByRestaurantIdAndCodeAndIdNotAndDeletedAtIsNull(RESTAURANT_ID, "DOWNTOWN", BRANCH_ID))
                .willReturn(true);

        assertThatThrownBy(() ->
                branchValidationService.normalizeAndValidateFields("downtown", "Downtown", RESTAURANT_ID, BRANCH_ID))
                .isInstanceOf(BranchCodeAlreadyExistsException.class);
    }

    @Test
    @DisplayName("validateManagerUser should return null when managerUserId is null")
    void shouldReturnNullWhenManagerUserIdIsNull() {
        UUID result = branchValidationService.validateManagerUser(null, RESTAURANT_ID);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("validateManagerUser should accept active manager in the same restaurant")
    void shouldAcceptValidManager() {
        given(userRepository.findByIdAndDeletedAtIsNull(MANAGER_ID))
                .willReturn(Optional.of(manager(MANAGER_ID, RESTAURANT_ID, true)));

        UUID result = branchValidationService.validateManagerUser(MANAGER_ID, RESTAURANT_ID);

        assertThat(result).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("validateManagerUser should reject missing manager")
    void shouldRejectMissingManager() {
        given(userRepository.findByIdAndDeletedAtIsNull(MANAGER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> branchValidationService.validateManagerUser(MANAGER_ID, RESTAURANT_ID))
                .isInstanceOf(BranchManagerNotFoundException.class);
    }

    @Test
    @DisplayName("validateManagerUser should reject inactive manager")
    void shouldRejectInactiveManager() {
        given(userRepository.findByIdAndDeletedAtIsNull(MANAGER_ID))
                .willReturn(Optional.of(manager(MANAGER_ID, RESTAURANT_ID, false)));

        assertThatThrownBy(() -> branchValidationService.validateManagerUser(MANAGER_ID, RESTAURANT_ID))
                .isInstanceOf(AuthException.class)
                .hasMessage("Manager user must be active")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateManagerUser should reject manager from a different restaurant")
    void shouldRejectManagerFromDifferentRestaurant() {
        given(userRepository.findByIdAndDeletedAtIsNull(MANAGER_ID))
                .willReturn(Optional.of(manager(MANAGER_ID, OTHER_RESTAURANT_ID, true)));

        assertThatThrownBy(() -> branchValidationService.validateManagerUser(MANAGER_ID, RESTAURANT_ID))
                .isInstanceOf(AuthException.class)
                .hasMessage("Manager user must belong to the same restaurant")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateStatusConsistency should accept valid active+ACTIVE pair")
    void shouldAcceptActiveAndActiveStatus() {
        assertThatCode(() -> branchValidationService.validateStatusConsistency(true, BranchStatus.ACTIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStatusConsistency should accept valid inactive+INACTIVE pair")
    void shouldAcceptInactiveAndInactiveStatus() {
        assertThatCode(() -> branchValidationService.validateStatusConsistency(false, BranchStatus.INACTIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStatusConsistency should accept valid inactive+TEMPORARILY_CLOSED pair")
    void shouldAcceptInactiveAndTemporarilyClosed() {
        assertThatCode(() -> branchValidationService.validateStatusConsistency(false, BranchStatus.TEMPORARILY_CLOSED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStatusConsistency should reject active=true with non-ACTIVE status")
    void shouldRejectActiveTrueWithNonActiveStatus() {
        assertThatThrownBy(() -> branchValidationService.validateStatusConsistency(true, BranchStatus.TEMPORARILY_CLOSED))
                .isInstanceOf(AuthException.class)
                .hasMessage("Non-active branch statuses must have isActive=false")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateStatusConsistency should reject active=false with ACTIVE status")
    void shouldRejectActiveFalseWithActiveStatus() {
        assertThatThrownBy(() -> branchValidationService.validateStatusConsistency(false, BranchStatus.ACTIVE))
                .isInstanceOf(AuthException.class)
                .hasMessage("ACTIVE branches must have isActive=true")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateStatusConsistency should pass when both values are null")
    void shouldPassWhenBothNull() {
        assertThatCode(() -> branchValidationService.validateStatusConsistency(null, null))
                .doesNotThrowAnyException();
    }

    private User manager(UUID userId, UUID restaurantId, boolean active) {
        return User.builder()
                .id(userId)
                .email("manager@pos.local")
                .username("manager")
                .passwordHash("stored")
                .firstName("Manager")
                .lastName("User")
                .status(active ? "ACTIVE" : "INACTIVE")
                .isActive(active)
                .restaurantId(restaurantId)
                .build();
    }
}
