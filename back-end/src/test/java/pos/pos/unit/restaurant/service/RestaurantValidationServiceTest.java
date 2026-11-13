package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantOwnerNotFoundException;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantValidationService")
class RestaurantValidationServiceTest {

    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID OTHER_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RestaurantValidationService restaurantValidationService;

    @Test
    @DisplayName("normalizeAndGenerateUniqueRegistrationFields should keep the base identifiers when available")
    void shouldKeepBaseIdentifiersWhenAvailable() {
        given(restaurantRepository.existsByCode("BURGER_HOUSE")).willReturn(false);
        given(restaurantRepository.existsBySlug("burger-house")).willReturn(false);

        RestaurantValidationService.NormalizedRestaurantFields response =
                restaurantValidationService.normalizeAndGenerateUniqueRegistrationFields(
                        "Burger House",
                        "America/New_York"
                );

        assertThat(response.code()).isEqualTo("BURGER_HOUSE");
        assertThat(response.slug()).isEqualTo("burger-house");
    }

    @Test
    @DisplayName("normalizeAndGenerateUniqueRegistrationFields should append a numeric suffix when the base identifiers are taken")
    void shouldAppendNumericSuffixWhenBaseIdentifiersAreTaken() {
        given(restaurantRepository.existsByCode("BURGER_HOUSE")).willReturn(false);
        given(restaurantRepository.existsByCode("BURGER_HOUSE_2")).willReturn(false);
        given(restaurantRepository.existsBySlug("burger-house")).willReturn(true);
        given(restaurantRepository.existsBySlug("burger-house-2")).willReturn(false);

        RestaurantValidationService.NormalizedRestaurantFields response =
                restaurantValidationService.normalizeAndGenerateUniqueRegistrationFields(
                        "Burger House",
                        "America/New_York"
                );

        assertThat(response.code()).isEqualTo("BURGER_HOUSE_2");
        assertThat(response.slug()).isEqualTo("burger-house-2");
    }

    @Test
    @DisplayName("validateOwnerUser should allow active owners that are unassigned or already in the restaurant")
    void shouldAllowAssignableOwnerUser() {
        given(userRepository.findByIdAndDeletedAtIsNull(OWNER_ID)).willReturn(Optional.of(owner(OWNER_ID, null, true)));

        UUID result = restaurantValidationService.validateOwnerUser(OWNER_ID, RESTAURANT_ID, RestaurantStatus.ACTIVE);

        assertThat(result).isEqualTo(OWNER_ID);
    }

    @Test
    @DisplayName("validateOwnerUser should reject missing owners")
    void shouldRejectMissingOwnerUser() {
        given(userRepository.findByIdAndDeletedAtIsNull(OWNER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantValidationService.validateOwnerUser(OWNER_ID, RESTAURANT_ID, RestaurantStatus.ACTIVE))
                .isInstanceOf(RestaurantOwnerNotFoundException.class);
    }

    @Test
    @DisplayName("validateOwnerUser should reject inactive owners")
    void shouldRejectInactiveOwnerUser() {
        given(userRepository.findByIdAndDeletedAtIsNull(OWNER_ID)).willReturn(Optional.of(owner(OWNER_ID, RESTAURANT_ID, false)));

        assertThatThrownBy(() -> restaurantValidationService.validateOwnerUser(OWNER_ID, RESTAURANT_ID, RestaurantStatus.ACTIVE))
                .isInstanceOf(AuthException.class)
                .hasMessage("Owner user must be active")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateOwnerUser should reject owners already assigned to another restaurant")
    void shouldRejectOwnerAssignedToAnotherRestaurant() {
        given(userRepository.findByIdAndDeletedAtIsNull(OWNER_ID))
                .willReturn(Optional.of(owner(OWNER_ID, OTHER_RESTAURANT_ID, true)));

        assertThatThrownBy(() -> restaurantValidationService.validateOwnerUser(OWNER_ID, RESTAURANT_ID, RestaurantStatus.ACTIVE))
                .isInstanceOf(AuthException.class)
                .hasMessage("Owner user is already assigned to another restaurant")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateOwnerUser should allow null owner for pending and rejected restaurants")
    void shouldAllowNullOwnerForRegistrationStatuses() {
        assertThat(restaurantValidationService.validateOwnerUser(null, RESTAURANT_ID, RestaurantStatus.PENDING)).isNull();
        assertThat(restaurantValidationService.validateOwnerUser(null, RESTAURANT_ID, RestaurantStatus.REJECTED)).isNull();
    }

    @Test
    @DisplayName("validateOwnerUser allows setup before an owner is invited")
    void shouldAllowNullOwnerForOperationalStatuses() {
        assertThat(restaurantValidationService.validateOwnerUser(null, RESTAURANT_ID, RestaurantStatus.ACTIVE)).isNull();
    }

    @Test
    @DisplayName("validateStatusConsistency should accept isActive=true with ACTIVE status")
    void shouldAcceptActiveTrueAndActiveStatus() {
        assertThatCode(() -> restaurantValidationService.validateStatusConsistency(true, RestaurantStatus.ACTIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStatusConsistency should accept isActive=false with INACTIVE status")
    void shouldAcceptActiveFalseAndInactiveStatus() {
        assertThatCode(() -> restaurantValidationService.validateStatusConsistency(false, RestaurantStatus.INACTIVE))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateStatusConsistency should reject isActive=true with non-ACTIVE status")
    void shouldRejectActiveTrueWithNonActiveStatus() {
        assertThatThrownBy(() -> restaurantValidationService.validateStatusConsistency(true, RestaurantStatus.SUSPENDED))
                .isInstanceOf(AuthException.class)
                .hasMessage("Non-active restaurant statuses must have isActive=false")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateStatusConsistency should reject isActive=false with ACTIVE status")
    void shouldRejectActiveFalseWithActiveStatus() {
        assertThatThrownBy(() -> restaurantValidationService.validateStatusConsistency(false, RestaurantStatus.ACTIVE))
                .isInstanceOf(AuthException.class)
                .hasMessage("ACTIVE restaurants must have isActive=true")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateStatusConsistency should pass when both values are null")
    void shouldPassWhenBothNull() {
        assertThatCode(() -> restaurantValidationService.validateStatusConsistency(null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateManageableStatus should reject PENDING status")
    void shouldRejectPendingStatus() {
        assertThatThrownBy(() -> restaurantValidationService.validateManageableStatus(RestaurantStatus.PENDING))
                .isInstanceOf(AuthException.class)
                .hasMessage("PENDING and REJECTED statuses are reserved for registration review")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateManageableStatus should reject REJECTED status")
    void shouldRejectRejectedStatus() {
        assertThatThrownBy(() -> restaurantValidationService.validateManageableStatus(RestaurantStatus.REJECTED))
                .isInstanceOf(AuthException.class)
                .hasMessage("PENDING and REJECTED statuses are reserved for registration review")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("validateManageableStatus should allow non-terminal manageable statuses")
    void shouldAllowManageableStatuses() {
        assertThatCode(() -> restaurantValidationService.validateManageableStatus(RestaurantStatus.ACTIVE))
                .doesNotThrowAnyException();
        assertThatCode(() -> restaurantValidationService.validateManageableStatus(RestaurantStatus.INACTIVE))
                .doesNotThrowAnyException();
        assertThatCode(() -> restaurantValidationService.validateManageableStatus(RestaurantStatus.SUSPENDED))
                .doesNotThrowAnyException();
        assertThatCode(() -> restaurantValidationService.validateManageableStatus(null))
                .doesNotThrowAnyException();
    }

    private User owner(UUID ownerId, UUID restaurantId, boolean active) {
        return User.builder()
                .id(ownerId)
                .email("owner@pos.local")
                .username("owner")
                .passwordHash("stored")
                .firstName("Olivia")
                .lastName("Owner")
                .status(active ? "ACTIVE" : "INACTIVE")
                .isActive(active)
                .restaurantId(restaurantId)
                .build();
    }

    @Test
    @DisplayName("normalizeAndGenerateUniqueRegistrationFields skips identifiers an archived restaurant still holds")
    void shouldSkipIdentifiersHeldByArchivedRestaurants() {
        // existsByCode counts archived restaurants too, so their code is never generated again.
        given(restaurantRepository.existsByCode("BURGER_HOUSE")).willReturn(true);
        given(restaurantRepository.existsByCode("BURGER_HOUSE_2")).willReturn(false);
        given(restaurantRepository.existsBySlug("burger-house-2")).willReturn(false);

        RestaurantValidationService.NormalizedRestaurantFields response =
                restaurantValidationService.normalizeAndGenerateUniqueRegistrationFields("Burger House", "Europe/Tirane");

        assertThat(response.code()).isEqualTo("BURGER_HOUSE_2");
        assertThat(response.slug()).isEqualTo("burger-house-2");
    }

    @Test
    @DisplayName("normalizeAndValidateFields reports a code kept by an archived restaurant as a reserved conflict")
    void shouldReserveArchivedRestaurantCode() {
        given(restaurantRepository.existsByCode("OLD_PLACE")).willReturn(true);
        given(restaurantRepository.existsByCodeAndDeletedAtIsNotNull("OLD_PLACE")).willReturn(true);

        assertThatThrownBy(() -> restaurantValidationService.normalizeAndValidateFields("old place", "old-place", "Old Place", "Europe/Tirane", null))
                .isInstanceOf(pos.pos.exception.restaurant.RestaurantCodeAlreadyExistsException.class)
                .hasMessageContaining("removed restaurant and stays reserved")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("normalizeAndValidateFields reports a code used by a live restaurant as a plain conflict")
    void shouldRejectLiveRestaurantCode() {
        given(restaurantRepository.existsByCodeAndIdNot("TAKEN", RESTAURANT_ID)).willReturn(true);
        given(restaurantRepository.existsByCodeAndDeletedAtIsNotNull("TAKEN")).willReturn(false);

        assertThatThrownBy(() -> restaurantValidationService.normalizeAndValidateFields("taken", "taken", "Taken", "Europe/Tirane", RESTAURANT_ID))
                .isInstanceOf(pos.pos.exception.restaurant.RestaurantCodeAlreadyExistsException.class)
                .hasMessage("Restaurant code already in use")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("normalizeAndValidateFields reports a web address kept by an archived restaurant as reserved")
    void shouldReserveArchivedRestaurantSlug() {
        given(restaurantRepository.existsByCode("FRESH_CODE")).willReturn(false);
        given(restaurantRepository.existsBySlug("old-place")).willReturn(true);
        given(restaurantRepository.existsBySlugAndDeletedAtIsNotNull("old-place")).willReturn(true);

        assertThatThrownBy(() -> restaurantValidationService.normalizeAndValidateFields("fresh code", "old-place", "Old Place", "Europe/Tirane", null))
                .isInstanceOf(pos.pos.exception.restaurant.RestaurantSlugAlreadyExistsException.class)
                .hasMessageContaining("removed restaurant and stays reserved")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("normalizeAndValidateFields lets a restaurant keep its own code and web address")
    void shouldAllowOwnIdentifiersOnUpdate() {
        given(restaurantRepository.existsByCodeAndIdNot("MINE", RESTAURANT_ID)).willReturn(false);
        given(restaurantRepository.existsBySlugAndIdNot("mine", RESTAURANT_ID)).willReturn(false);

        RestaurantValidationService.NormalizedRestaurantFields fields =
                restaurantValidationService.normalizeAndValidateFields("mine", "mine", "Mine", "Europe/Tirane", RESTAURANT_ID);

        assertThat(fields.code()).isEqualTo("MINE");
        assertThat(fields.slug()).isEqualTo("mine");
    }
}
