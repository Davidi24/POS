package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pos.pos.auth.service.OneTimeCodeService;
import pos.pos.exception.auth.InvalidTokenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OneTimeCodeService")
class OneTimeCodeServiceTest {

    private final OneTimeCodeService oneTimeCodeService = new OneTimeCodeService();

    @Nested
    @DisplayName("issueNumericCode")
    class IssueNumericCodeTests {

        @Test
        @DisplayName("Should issue numeric code with matching hash")
        void shouldIssueNumericCodeWithMatchingHash() {
            OneTimeCodeService.IssuedCode issuedCode = oneTimeCodeService.issueNumericCode(6, "pepper");

            assertThat(issuedCode.rawCode()).matches("\\d{6}");
            assertThat(issuedCode.codeHash()).isEqualTo(oneTimeCodeService.hash(issuedCode.rawCode(), "pepper"));
        }

        @Test
        @DisplayName("Should support minimum code length")
        void shouldSupportMinimumCodeLength() {
            OneTimeCodeService.IssuedCode issuedCode = oneTimeCodeService.issueNumericCode(4, "pepper");

            assertThat(issuedCode.rawCode()).matches("\\d{4}");
        }

        @Test
        @DisplayName("Should support maximum code length")
        void shouldSupportMaximumCodeLength() {
            OneTimeCodeService.IssuedCode issuedCode = oneTimeCodeService.issueNumericCode(8, "pepper");

            assertThat(issuedCode.rawCode()).matches("\\d{8}");
        }

        @Test
        @DisplayName("Should reject code lengths below minimum")
        void shouldRejectCodeLengthsBelowMinimum() {
            assertThatThrownBy(() -> oneTimeCodeService.issueNumericCode(3, "pepper"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Code length must be between 4 and 8 digits");
        }

        @Test
        @DisplayName("Should reject code lengths above maximum")
        void shouldRejectCodeLengthsAboveMaximum() {
            assertThatThrownBy(() -> oneTimeCodeService.issueNumericCode(9, "pepper"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Code length must be between 4 and 8 digits");
        }
    }

    @Nested
    @DisplayName("hash")
    class HashTests {

        @Test
        @DisplayName("Should trim code input before hashing")
        void shouldTrimCodeInputBeforeHashing() {
            String trimmedHash = oneTimeCodeService.hash("123456", "pepper");
            String spacedHash = oneTimeCodeService.hash(" 123456 ", "pepper");

            assertThat(spacedHash).isEqualTo(trimmedHash);
        }

        @Test
        @DisplayName("Should produce deterministic hash for same code and pepper")
        void shouldProduceDeterministicHashForSameCodeAndPepper() {
            String firstHash = oneTimeCodeService.hash("123456", "pepper");
            String secondHash = oneTimeCodeService.hash("123456", "pepper");

            assertThat(firstHash).isEqualTo(secondHash);
        }

        @Test
        @DisplayName("Should produce different hash when pepper changes")
        void shouldProduceDifferentHashWhenPepperChanges() {
            String firstHash = oneTimeCodeService.hash("123456", "pepper-a");
            String secondHash = oneTimeCodeService.hash("123456", "pepper-b");

            assertThat(firstHash).isNotEqualTo(secondHash);
        }

        @Test
        @DisplayName("Should reject blank codes")
        void shouldRejectBlankCodes() {
            assertThatThrownBy(() -> oneTimeCodeService.hash("   ", "pepper"))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    @DisplayName("normalize")
    class NormalizeTests {

        @Test
        @DisplayName("Should trim surrounding whitespace")
        void shouldTrimSurroundingWhitespace() {
            assertThat(oneTimeCodeService.normalize(" 123456 ")).isEqualTo("123456");
        }

        @Test
        @DisplayName("Should reject null values")
        void shouldRejectNullValues() {
            assertThatThrownBy(() -> oneTimeCodeService.normalize(null))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
