package pos.pos.unit.user.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.role.entity.Role;
import pos.pos.user.entity.UserRole;
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

    @Nested
    @DisplayName("findByIdAndDeletedAtIsNull")
    class FindByIdAndDeletedAtIsNullTests {

        @Test
        @DisplayName("Should return the non-deleted user")
        void shouldReturnNonDeletedUser() {
            User user = repository.save(user("read@pos.local", "read.user", true, null));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findByIdAndDeletedAtIsNull(user.getId());

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("Should return empty for soft-deleted user")
        void shouldReturnEmptyForDeletedUser() {
            User user = repository.save(user("deleted@pos.local", "deleted.reader", true, OffsetDateTime.now(ZoneOffset.UTC)));
            repository.flush();
            entityManager.clear();

            Optional<User> result = repository.findByIdAndDeletedAtIsNull(user.getId());

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull")
    class ExistsByNormalizedPhoneAndIdNotTests {

        @Test
        @DisplayName("Should return true when another active user owns the phone")
        void shouldReturnTrueWhenAnotherActiveUserOwnsPhone() {
            User owner = repository.save(user(uniqueEmail("owner"), uniqueUsername("owner"), "+49-555-0100", true, null));
            User actor = repository.save(user(uniqueEmail("actor"), uniqueUsername("actor"), "+49-555-0101", true, null));
            repository.flush();
            entityManager.clear();

            boolean exists = repository.existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull("+495550100", actor.getId());

            assertThat(exists).isTrue();
            assertThat(owner.getId()).isNotEqualTo(actor.getId());
        }

        @Test
        @DisplayName("Should return false when the phone belongs to the same user")
        void shouldReturnFalseWhenPhoneBelongsToSameUser() {
            User owner = repository.save(user(uniqueEmail("owner"), uniqueUsername("owner"), "+49-555-0100", true, null));
            repository.flush();
            entityManager.clear();

            boolean exists = repository.existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull("+495550100", owner.getId());

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("searchVisibleUsers")
    class SearchVisibleUsersTests {

        @Test
        @DisplayName("Should return only users visible to a non-super-admin with the requested filters")
        void shouldReturnVisibleUsersForNonSuperAdmin() {
            String managerCode = uniqueCode("MANAGER");
            String waiterCode = uniqueCode("WAITER");
            String protectedCode = uniqueCode("PROTECTED");

            Role managerRole = persistRole(managerCode, uniqueName("Manager"), 20_000L, true, false);
            Role waiterRole = persistRole(waiterCode, uniqueName("Waiter"), 10_000L, true, false);
            Role protectedRole = persistRole(protectedCode, uniqueName("Protected"), 100_000L, false, true);

            User visibleUser = repository.save(user(uniqueEmail("visible"), uniqueUsername("visible"), "+49-555-0100", true, null));
            User hiddenProtectedUser = repository.save(user(uniqueEmail("protected"), uniqueUsername("protected"), "+49-555-0101", true, null));
            User inactiveUser = repository.save(user(uniqueEmail("inactive"), uniqueUsername("inactive"), "+49-555-0102", false, null));
            User deletedUser = repository.save(user(uniqueEmail("deleted"), uniqueUsername("deleted"), "+49-555-0103", true, OffsetDateTime.now(ZoneOffset.UTC)));

            persistAssignment(visibleUser, waiterRole);
            persistAssignment(hiddenProtectedUser, protectedRole);
            persistAssignment(inactiveUser, waiterRole);
            persistAssignment(deletedUser, waiterRole);
            repository.flush();
            entityManager.clear();

            Page<User> page = repository.searchVisibleUsers(
                    true,
                    "%visible%",
                    "%+495550100%",
                    waiterCode,
                    false,
                    managerRole.getRank(),
                    PageRequest.of(0, 20)
            );

            assertThat(page.getContent()).extracting(User::getId).containsExactly(visibleUser.getId());
        }

        @Test
        @DisplayName("Should include protected higher-ranked users for super admin")
        void shouldIncludeProtectedHigherRankedUsersForSuperAdmin() {
            Role waiterRole = persistRole(uniqueCode("WAITER"), uniqueName("Waiter"), 10_000L, true, false);
            Role protectedRole = persistRole(uniqueCode("PROTECTED"), uniqueName("Protected"), 100_000L, false, true);

            User visibleUser = repository.save(user(uniqueEmail("visible"), uniqueUsername("visible"), "+49-555-0100", true, null));
            User protectedUser = repository.save(user(uniqueEmail("protected"), uniqueUsername("protected"), "+49-555-0101", true, null));

            persistAssignment(visibleUser, waiterRole);
            persistAssignment(protectedUser, protectedRole);
            repository.flush();
            entityManager.clear();

            Page<User> page = repository.searchVisibleUsers(
                    null,
                    null,
                    null,
                    null,
                    true,
                    Long.MAX_VALUE,
                    PageRequest.of(0, 20)
            );

            assertThat(page.getContent()).extracting(User::getId)
                    .contains(visibleUser.getId(), protectedUser.getId());
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

    private Role persistRole(String code, String name, long rank, boolean assignable, boolean protectedRole) {
        Role role = Role.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .rank(rank)
                .isActive(true)
                .assignable(assignable)
                .protectedRole(protectedRole)
                .build();
        entityManager.persist(role);
        return role;
    }

    private void persistAssignment(User user, Role role) {
        entityManager.persist(UserRole.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .roleId(role.getId())
                .assignedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());
    }

    private String uniqueEmail(String prefix) {
        return prefix + "." + UUID.randomUUID() + "@pos.local";
    }

    private String uniqueUsername(String prefix) {
        return prefix + "." + UUID.randomUUID();
    }

    private String uniqueCode(String prefix) {
        return (prefix + "_" + UUID.randomUUID().toString().replace("-", "")).toUpperCase();
    }

    private String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }
}
