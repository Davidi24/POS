package pos.pos.unit.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.security.service.OpaqueTokenService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OpaqueTokenService")
class OpaqueTokenServiceTest {

    private static final String PEPPER = "opaque-token-pepper";

    private final OpaqueTokenService opaqueTokenService = new OpaqueTokenService();

    @Nested
    @DisplayName("generate")
    class GenerateTests {

        @Test
        @DisplayName("Should generate URL-safe token without padding")
        void shouldGenerateUrlSafeTokenWithoutPadding() {
            String token = opaqueTokenService.generate();

            assertThat(token)
                    .isNotBlank()
                    .hasSize(43)
                    .matches("[A-Za-z0-9_-]+");
        }

        @Test
        @DisplayName("Should generate different tokens on successive calls")
        void shouldGenerateDifferentTokensOnSuccessiveCalls() {
            String firstToken = opaqueTokenService.generate();
            String secondToken = opaqueTokenService.generate();

            assertThat(firstToken).isNotEqualTo(secondToken);
        }
    }

    @Nested
    @DisplayName("issue")
    class IssueTests {

        @Test
        @DisplayName("Should return raw token and matching hash")
        void shouldReturnRawTokenAndMatchingHash() {
            OpaqueTokenService.IssuedToken issuedToken = opaqueTokenService.issue(PEPPER);

            assertThat(issuedToken.rawToken()).isNotBlank();
            assertThat(issuedToken.tokenHash())
                    .isEqualTo(opaqueTokenService.hash(issuedToken.rawToken(), PEPPER));
        }
    }

    @Nested
    @DisplayName("normalize")
    class NormalizeTests {

        @Test
        @DisplayName("Should trim surrounding whitespace")
        void shouldTrimSurroundingWhitespace() {
            assertThat(opaqueTokenService.normalize("  token-value  "))
                    .isEqualTo("token-value");
        }

        @Test
        @DisplayName("Should reject blank token")
        void shouldRejectBlankToken() {
            assertThatThrownBy(() -> opaqueTokenService.normalize("   "))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        @DisplayName("Should reject null token")
        void shouldRejectNullToken() {
            assertThatThrownBy(() -> opaqueTokenService.normalize(null))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    @DisplayName("hash")
    class HashTests {

        @Test
        @DisplayName("Should hash deterministically for same token and pepper")
        void shouldHashDeterministicallyForSameTokenAndPepper() {
            String firstHash = opaqueTokenService.hash("token-value", PEPPER);
            String secondHash = opaqueTokenService.hash("token-value", PEPPER);

            assertThat(firstHash).isEqualTo(secondHash);
        }

        @Test
        @DisplayName("Should trim token before hashing")
        void shouldTrimTokenBeforeHashing() {
            String trimmedHash = opaqueTokenService.hash("token-value", PEPPER);
            String spacedHash = opaqueTokenService.hash("  token-value  ", PEPPER);

            assertThat(spacedHash).isEqualTo(trimmedHash);
        }

        @Test
        @DisplayName("Should produce different hash when pepper changes")
        void shouldProduceDifferentHashWhenPepperChanges() {
            String firstHash = opaqueTokenService.hash("token-value", "pepper-a");
            String secondHash = opaqueTokenService.hash("token-value", "pepper-b");

            assertThat(firstHash).isNotEqualTo(secondHash);
        }

        @Test
        @DisplayName("Should reject blank token when hashing")
        void shouldRejectBlankTokenWhenHashing() {
            assertThatThrownBy(() -> opaqueTokenService.hash("   ", PEPPER))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }
}
