package pos.pos.user.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.user.entity.User;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User createUser() {
        OffsetDateTime now = OffsetDateTime.now();

        return User.builder()
                .email("test@test.com")
                .passwordHash("hash")
                .firstName("John")
                .lastName("Doe")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    @Test
    @DisplayName("existsByEmail returns true when email exists")
    void existsByEmail_shouldReturnTrue_whenEmailExists() {

        User user = createUser();
        userRepository.save(user);

        boolean exists = userRepository.existsByEmail("test@test.com");

        assertTrue(exists);
    }

    @Test
    @DisplayName("existsByEmail returns false when email does not exist")
    void existsByEmail_shouldReturnFalse_whenEmailNotExists() {

        boolean exists = userRepository.existsByEmail("missing@test.com");

        assertFalse(exists);
    }

    @Test
    @DisplayName("findByEmail returns user when email exists")
    void findByEmail_shouldReturnUser_whenEmailExists() {

        User user = createUser();
        userRepository.save(user);

        Optional<User> result = userRepository.findByEmail("test@test.com");

        assertTrue(result.isPresent());
        assertEquals("test@test.com", result.get().getEmail());
    }

    @Test
    @DisplayName("findByEmail returns empty when email not found")
    void findByEmail_shouldReturnEmpty_whenEmailNotFound() {

        Optional<User> result = userRepository.findByEmail("missing@test.com");

        assertTrue(result.isEmpty());
    }
}
