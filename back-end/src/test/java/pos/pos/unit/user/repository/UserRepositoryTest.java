package pos.pos.unit.user.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Nested
    @DisplayName("findByEmailAndDeletedAtIsNull")
    class FindByEmailTests {

        @Test
        @DisplayName("Should find active non-deleted user by normalized email")
        void shouldFindNonDeletedUserByEmail() {
            User user = repository.save(user("  Cashier@POS.local  ", "cashier.one", true, null));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findByEmailAndDeletedAtIsNull("cashier@pos.local");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(user.getId());
            assertThat(result.get().getEmail()).isEqualTo("cashier@pos.local");
        }

        @Test
        @DisplayName("Should return empty when email does not exist")
        void shouldReturnEmpty_whenEmailDoesNotExist() {
            Optional<User> result = repository.findByEmailAndDeletedAtIsNull("missing@pos.local");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should not find soft-deleted user by email")
        void shouldNotFindDeletedUserByEmail() {
            repository.save(user("deleted@pos.local", "deleted.user", true, OffsetDateTime.now(ZoneOffset.UTC)));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findByEmailAndDeletedAtIsNull("deleted@pos.local");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByEmailAndDeletedAtIsNull")
    class ExistsByEmailTests {

        @Test
        @DisplayName("Should return true for existing non-deleted user")
        void shouldReturnTrueForExistingNonDeletedUser() {
            repository.save(user("cashier@pos.local", "cashier.one", true, null));
            repository.flush();
            entityManager.clear();

            boolean exists = repository.existsByEmailAndDeletedAtIsNull("cashier@pos.local");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should return false for soft-deleted user")
        void shouldReturnFalseForDeletedUser() {
            repository.save(user("deleted@pos.local", "deleted.user", true, OffsetDateTime.now(ZoneOffset.UTC)));
            repository.flush();
            entityManager.clear();

            boolean exists = repository.existsByEmailAndDeletedAtIsNull("deleted@pos.local");

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("Should return false when email does not exist")
        void shouldReturnFalseWhenEmailDoesNotExist() {
            boolean exists = repository.existsByEmailAndDeletedAtIsNull("missing@pos.local");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByUsernameAndDeletedAtIsNull")
    class FindByUsernameTests {

        @Test
        @DisplayName("Should find active non-deleted user by normalized username")
        void shouldFindNonDeletedUserByUsername() {
            User user = repository.save(user("cashier@pos.local", "  Cashier.One  ", true, null));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findByUsernameAndDeletedAtIsNull("cashier.one");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(user.getId());
            assertThat(result.get().getUsername()).isEqualTo("cashier.one");
        }

        @Test
        @DisplayName("Should return empty when username does not exist")
        void shouldReturnEmptyWhenUsernameDoesNotExist() {
            Optional<User> result = repository.findByUsernameAndDeletedAtIsNull("missing.user");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should not find soft-deleted user by username")
        void shouldNotFindDeletedUserByUsername() {
            repository.save(user("deleted@pos.local", "deleted.user", true, OffsetDateTime.now(ZoneOffset.UTC)));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findByUsernameAndDeletedAtIsNull("deleted.user");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByUsernameAndDeletedAtIsNull")
    class ExistsByUsernameTests {

        @Test
        @DisplayName("Should return true for existing non-deleted username")
        void shouldReturnTrueForExistingNonDeletedUsername() {
            repository.save(user("cashier@pos.local", "cashier.one", true, null));
            repository.flush();
            entityManager.clear();

            boolean exists = repository.existsByUsernameAndDeletedAtIsNull("cashier.one");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should return false for soft-deleted username")
        void shouldReturnFalseForDeletedUsername() {
            repository.save(user("deleted@pos.local", "deleted.user", true, OffsetDateTime.now(ZoneOffset.UTC)));
            repository.flush();
            entityManager.clear();

            boolean exists = repository.existsByUsernameAndDeletedAtIsNull("deleted.user");

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("Should return false when username does not exist")
        void shouldReturnFalseWhenUsernameDoesNotExist() {
            boolean exists = repository.existsByUsernameAndDeletedAtIsNull("missing.user");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findByNormalizedPhoneAndDeletedAtIsNull")
    class FindByNormalizedPhoneTests {

        @Test
        @DisplayName("Should find active non-deleted user by normalized phone")
        void shouldFindNonDeletedUserByNormalizedPhone() {
            User user = repository.save(user("cashier@pos.local", "cashier.one", " +49 (555) 01-00 ", true, null));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findByNormalizedPhoneAndDeletedAtIsNull("+495550100");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(user.getId());
            assertThat(result.get().getNormalizedPhone()).isEqualTo("+495550100");
        }

        @Test
        @DisplayName("Should return empty when normalized phone does not exist")
        void shouldReturnEmptyWhenNormalizedPhoneDoesNotExist() {
            Optional<User> result = repository.findByNormalizedPhoneAndDeletedAtIsNull("+495559999");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should not find soft-deleted user by normalized phone")
        void shouldNotFindDeletedUserByNormalizedPhone() {
            repository.save(user(
                    "deleted@pos.local",
                    "deleted.user",
                    "+49-555-0100",
                    true,
                    OffsetDateTime.now(ZoneOffset.UTC)
            ));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findByNormalizedPhoneAndDeletedAtIsNull("+495550100");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByNormalizedPhoneAndDeletedAtIsNull")
    class ExistsByNormalizedPhoneTests {

        @Test
        @DisplayName("Should return true for existing non-deleted normalized phone")
        void shouldReturnTrueForExistingNonDeletedNormalizedPhone() {
            repository.save(user("cashier@pos.local", "cashier.one", "+49-555-0100", true, null));
            repository.flush();
            entityManager.clear();

            boolean exists = repository.existsByNormalizedPhoneAndDeletedAtIsNull("+495550100");

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("Should return false for soft-deleted normalized phone")
        void shouldReturnFalseForDeletedNormalizedPhone() {
            repository.save(user(
                    "deleted@pos.local",
                    "deleted.user",
                    "+49-555-0100",
                    true,
                    OffsetDateTime.now(ZoneOffset.UTC)
            ));
            repository.flush();
            entityManager.clear();

            boolean exists = repository.existsByNormalizedPhoneAndDeletedAtIsNull("+495550100");

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("Should return false when normalized phone does not exist")
        void shouldReturnFalseWhenNormalizedPhoneDoesNotExist() {
            boolean exists = repository.existsByNormalizedPhoneAndDeletedAtIsNull("+495559999");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("findActiveById")
    class FindActiveByIdTests {

        @Test
        @DisplayName("Should find active non-deleted user by id")
        void shouldFindActiveNonDeletedUserById() {
            User user = repository.save(user("active@pos.local", "active.user", true, null));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findActiveById(user.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("Should not find inactive user by id")
        void shouldNotFindInactiveUserById() {
            User user = repository.save(user("inactive@pos.local", "inactive.user", false, null));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findActiveById(user.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should not find soft-deleted user by id")
        void shouldNotFindDeletedUserById() {
            User user = repository.save(user("deleted@pos.local", "deleted.user", true, OffsetDateTime.now(ZoneOffset.UTC)));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findActiveById(user.getId());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should return empty when user id does not exist")
        void shouldReturnEmptyWhenUserIdDoesNotExist() {
            Optional<User> result = repository.findActiveById(UUID.randomUUID());

            assertThat(result).isEmpty();
        }
    }

    private User user(String email, String username, boolean isActive, OffsetDateTime deletedAt) {
        return user(email, username, null, isActive, deletedAt);
    }

    private User user(String email, String username, String phone, boolean isActive, OffsetDateTime deletedAt) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .username(username)
                .passwordHash("stored-password-hash")
                .firstName("John")
                .lastName("Doe")
                .phone(phone)
                .status("ACTIVE")
                .isActive(isActive)
                .emailVerified(true)
                .deletedAt(deletedAt)
                .build();
    }
}
