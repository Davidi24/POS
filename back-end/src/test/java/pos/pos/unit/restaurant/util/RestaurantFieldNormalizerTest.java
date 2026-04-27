package pos.pos.unit.restaurant.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.restaurant.util.RestaurantFieldNormalizer;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RestaurantFieldNormalizer")
class RestaurantFieldNormalizerTest {

    @Test
    @DisplayName("Should normalize code using uppercase underscores")
    void shouldNormalizeCode() {
        assertThat(RestaurantFieldNormalizer.normalizeCode(" Main Restaurant 2026 "))
                .isEqualTo("MAIN_RESTAURANT_2026");
    }

    @Test
    @DisplayName("Should use fallback name when code is blank")
    void shouldUseFallbackForCode() {
        assertThat(RestaurantFieldNormalizer.normalizeCodeOrFallback("   ", "Main Restaurant"))
                .isEqualTo("MAIN_RESTAURANT");
    }

    @Test
    @DisplayName("Should normalize slug using lowercase hyphens")
    void shouldNormalizeSlug() {
        assertThat(RestaurantFieldNormalizer.normalizeSlug(" Main Restaurant 2026 "))
                .isEqualTo("main-restaurant-2026");
    }

    @Test
    @DisplayName("Should return null when slug has no usable characters")
    void shouldReturnNullForEmptySlug() {
        assertThat(RestaurantFieldNormalizer.normalizeSlug("%%%"))
                .isNull();
    }

    @Test
    @DisplayName("Should normalize and validate timezone")
    void shouldNormalizeAndValidateTimezone() {
        String timezone = RestaurantFieldNormalizer.normalizeTimezone(" Europe/Berlin ");

        assertThat(timezone).isEqualTo("Europe/Berlin");
        assertThat(RestaurantFieldNormalizer.isValidTimezone(timezone)).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid timezone values")
    void shouldRejectInvalidTimezone() {
        assertThat(RestaurantFieldNormalizer.isValidTimezone(RestaurantFieldNormalizer.normalizeTimezone("Mars/Phobos")))
                .isFalse();
        assertThat(RestaurantFieldNormalizer.isValidTimezone(RestaurantFieldNormalizer.normalizeTimezone("   ")))
                .isFalse();
    }
}
