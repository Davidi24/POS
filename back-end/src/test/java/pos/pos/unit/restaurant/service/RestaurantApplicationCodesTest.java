package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.restaurant.service.RestaurantApplicationCodes;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RestaurantApplicationCodes")
class RestaurantApplicationCodesTest {

    private final RestaurantApplicationCodes codes = new RestaurantApplicationCodes("unit-test-application-pepper");

    @Test
    @DisplayName("codes are four groups of five characters without look-alike letters, and never repeat")
    void shouldGenerateReadableUniqueCodes() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            String code = codes.newCode();
            assertThat(code).matches("[0-9ABCDEFGHJKMNPQRSTVWXYZ]{5}(-[0-9ABCDEFGHJKMNPQRSTVWXYZ]{5}){3}");
            assertThat(seen.add(code)).isTrue();
        }
    }

    @Test
    @DisplayName("the hash ignores case, spaces and dashes, and reads O as 0 and I or L as 1")
    void shouldHashTypedVariantsAlike() {
        String hash = codes.hash("01ABC-DEFGH-JKMNP-QRST1");

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(codes.hash(" 01abc defgh jkmnp qrst1 ")).isEqualTo(hash);
        assertThat(codes.hash("O1ABC-DEFGH-JKMNP-QRSTI")).isEqualTo(hash);
        assertThat(codes.hash("01ABC-DEFGH-JKMNP-QRSTL")).isEqualTo(hash);
        assertThat(codes.hash("01ABC-DEFGH-JKMNP-QRST2")).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("anything that can't be a code has no hash (too short, too long, foreign characters, empty)")
    void shouldRejectNonCodes() {
        assertThat(codes.hash(null)).isNull();
        assertThat(codes.hash("")).isNull();
        assertThat(codes.hash("01ABC-DEFGH-JKMNP-QRST")).isNull();
        assertThat(codes.hash("01ABC-DEFGH-JKMNP-QRST12")).isNull();
        assertThat(codes.hash("01ABC-DEFGH-JKMNP-QRSU1")).isNull();
        assertThat(codes.hash("01ABC-DEFGH-JKMNP-QRS%1")).isNull();
    }

    @Test
    @DisplayName("a different pepper gives a different hash, and a short pepper is refused")
    void shouldDependOnPepper() {
        String code = codes.newCode();
        assertThat(new RestaurantApplicationCodes("another-test-pepper-value").hash(code)).isNotEqualTo(codes.hash(code));
        assertThatThrownBy(() -> new RestaurantApplicationCodes("short")).isInstanceOf(IllegalStateException.class);
    }
}
