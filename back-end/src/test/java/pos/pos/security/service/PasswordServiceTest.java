package pos.pos.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {

    private final PasswordService passwordService = new PasswordService(new BCryptPasswordEncoder(12));

    @Nested
    @DisplayName("hash")
    class HashTests {

        @Test
        @DisplayName("Should hash password without storing the raw value")
        void shouldHashPasswordWithoutStoringRawValue() {
            String rawPassword = "SecurePass123!";

            String hashedPassword = passwordService.hash(rawPassword);

            assertThat(hashedPassword).isNotBlank();
            assertThat(hashedPassword).isNotEqualTo(rawPassword);
            assertThat(passwordService.matches(rawPassword, hashedPassword)).isTrue();
        }

        @Test
        @DisplayName("Should generate different hashes for the same password")
        void shouldGenerateDifferentHashesForSamePassword() {
            String rawPassword = "SecurePass123!";

            String firstHash = passwordService.hash(rawPassword);
            String secondHash = passwordService.hash(rawPassword);

            assertThat(firstHash).isNotEqualTo(secondHash);
            assertThat(passwordService.matches(rawPassword, firstHash)).isTrue();
            assertThat(passwordService.matches(rawPassword, secondHash)).isTrue();
        }
    }

    @Nested
    @DisplayName("matches")
    class MatchesTests {

        @Test
        @DisplayName("Should return true for matching password and hash")
        void shouldReturnTrueForMatchingPasswordAndHash() {
            String rawPassword = "SecurePass123!";
            String hashedPassword = passwordService.hash(rawPassword);

            boolean matches = passwordService.matches(rawPassword, hashedPassword);

            assertThat(matches).isTrue();
        }

        @Test
        @DisplayName("Should return false for wrong password")
        void shouldReturnFalseForWrongPassword() {
            String hashedPassword = passwordService.hash("SecurePass123!");

            boolean matches = passwordService.matches("WrongPass123!", hashedPassword);

            assertThat(matches).isFalse();
        }
    }
}
