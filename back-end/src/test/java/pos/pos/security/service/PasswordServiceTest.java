package pos.pos.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordServiceTest {

    private PasswordService passwordService;

    @BeforeEach
    void setup() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        passwordService = new PasswordService(encoder);
    }

    @Test
    void hash_shouldReturnHashedPassword() {

        String raw = "Password123";

        String hashed = passwordService.hash(raw);

        assertNotNull(hashed);
        assertNotEquals(raw, hashed);
    }

    @Test
    void matches_shouldReturnTrue_whenPasswordCorrect() {

        String raw = "Password123";
        String hashed = passwordService.hash(raw);

        boolean result = passwordService.matches(raw, hashed);

        assertTrue(result);
    }

    @Test
    void matches_shouldReturnFalse_whenPasswordIncorrect() {

        String raw = "Password123";
        String hashed = passwordService.hash(raw);

        boolean result = passwordService.matches("WrongPassword", hashed);

        assertFalse(result);
    }

    @Test
    void hash_shouldGenerateDifferentHashes_forSamePassword() {

        String raw = "Password123";

        String hash1 = passwordService.hash(raw);
        String hash2 = passwordService.hash(raw);

        assertNotEquals(hash1, hash2);
    }
}