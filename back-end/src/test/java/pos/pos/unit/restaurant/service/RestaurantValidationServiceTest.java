package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantValidationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantValidationService")
class RestaurantValidationServiceTest {

    @Mock
    private RestaurantRepository restaurantRepository;

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
}
