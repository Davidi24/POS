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
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        given(restaurantRepository.existsByCodeAndDeletedAtIsNull("BURGER_HOUSE")).willReturn(false);
        given(restaurantRepository.existsBySlugAndDeletedAtIsNull("burger-house")).willReturn(false);

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
        given(restaurantRepository.existsByCodeAndDeletedAtIsNull("BURGER_HOUSE")).willReturn(false);
        given(restaurantRepository.existsByCodeAndDeletedAtIsNull("BURGER_HOUSE_2")).willReturn(false);
        given(restaurantRepository.existsBySlugAndDeletedAtIsNull("burger-house")).willReturn(true);
        given(restaurantRepository.existsBySlugAndDeletedAtIsNull("burger-house-2")).willReturn(false);

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

        UUID result = restaurantValidationService.validateOwnerUser(OWNER_ID, RESTAURANT_ID);

        assertThat(result).isEqualTo(OWNER_ID);
    }

    @Test
    @DisplayName("validateOwnerUser should reject missing owners")
    void shouldRejectMissingOwnerUser() {
        given(userRepository.findByIdAndDeletedAtIsNull(OWNER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantValidationService.validateOwnerUser(OWNER_ID, RESTAURANT_ID))
                .isInstanceOf(RestaurantOwnerNotFoundException.class);
    }

    @Test
    @DisplayName("validateOwnerUser should reject inactive owners")
    void shouldRejectInactiveOwnerUser() {
        given(userRepository.findByIdAndDeletedAtIsNull(OWNER_ID)).willReturn(Optional.of(owner(OWNER_ID, RESTAURANT_ID, false)));

        assertThatThrownBy(() -> restaurantValidationService.validateOwnerUser(OWNER_ID, RESTAURANT_ID))
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

        assertThatThrownBy(() -> restaurantValidationService.validateOwnerUser(OWNER_ID, RESTAURANT_ID))
                .isInstanceOf(AuthException.class)
                .hasMessage("Owner user is already assigned to another restaurant")
                .extracting(ex -> ((AuthException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
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
}
