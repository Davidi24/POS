package pos.pos.unit.role.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.user.entity.UserRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleRepositoryTest {

    @Autowired
    private RoleRepository repository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void cleanDatabase() {
        entityManager.createQuery("DELETE FROM UserRole").executeUpdate();
        entityManager.createQuery("DELETE FROM Role").executeUpdate();
        entityManager.clear();
    }

    @Nested
    @DisplayName("findByIdIn")
    class FindByIdInTests {

        @Test
        @DisplayName("Should return only roles whose ids are requested")
        void shouldReturnOnlyRolesWithRequestedIds() {
            Role admin = repository.save(role("admin", "Admin", true));
            Role cashier = repository.save(role("cashier", "Cashier", true));
            repository.save(role("manager", "Manager", true));
            repository.flush();
            entityManager.clear();

            List<Role> result = repository.findByIdIn(List.of(admin.getId(), cashier.getId()));

            assertThat(result)
                    .extracting(Role::getId)
                    .containsExactlyInAnyOrder(admin.getId(), cashier.getId());
        }
    }

    @Nested
    @DisplayName("findByCode")
    class FindByCodeTests {

        @Test
        @DisplayName("Should find role by normalized code")
        void shouldFindRoleByNormalizedCode() {
            repository.save(role("admin", "Admin", true));
            repository.flush();
            entityManager.clear();

            Optional<Role> result = repository.findByCode("ADMIN");

            assertThat(result).isPresent();
            assertThat(result.get().getCode()).isEqualTo("ADMIN");
            assertThat(result.get().getName()).isEqualTo("Admin");
        }

        @Test
        @DisplayName("Should return empty when code does not exist")
        void shouldReturnEmptyWhenCodeDoesNotExist() {
            Optional<Role> result = repository.findByCode("MISSING");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIsActiveTrue")
    class FindActiveRolesTests {

        @Test
        @DisplayName("Should return only active roles")
        void shouldReturnOnlyActiveRoles() {
            repository.save(role("admin", "Admin", true));
            repository.save(role("cashier", "Cashier", true));
            repository.save(role("manager", "Manager", false));
            repository.flush();
            entityManager.clear();

            List<Role> result = repository.findByIsActiveTrue();

            assertThat(result)
                    .extracting(Role::getCode)
                    .containsExactlyInAnyOrder("ADMIN", "CASHIER");
        }
    }

    @Nested
    @DisplayName("findByIsActiveTrueOrderByRankDescNameAsc")
    class FindActiveRolesOrderedTests {

        @Test
        @DisplayName("Should return active roles ordered by rank descending then name ascending")
        void shouldReturnActiveRolesOrderedByRankThenName() {
            repository.save(role("inactive_top", "Inactive Top", 30_000L, false, true, false));
            repository.save(role("beta_manager", "Beta Manager", 20_000L, true, true, false));
            repository.save(role("alpha_manager", "Alpha Manager", 20_000L, true, true, false));
            repository.save(role("cashier", "Cashier", 10_000L, true, true, false));
            repository.flush();
            entityManager.clear();

            List<Role> result = repository.findByIsActiveTrueOrderByRankDescNameAsc();

            assertThat(result)
                    .extracting(Role::getCode)
                    .containsExactly("ALPHA_MANAGER", "BETA_MANAGER", "CASHIER");
        }
    }

    @Nested
    @DisplayName("findAssignableRolesForActorRank")
    class FindAssignableRolesForActorRankTests {

        @Test
        @DisplayName("Should return only active assignable unprotected roles below actor rank in sorted order")
        void shouldReturnOnlyAssignableRolesBelowActorRank() {
            repository.save(role("manager", "Manager", 20_000L, true, true, false));
            repository.save(role("supervisor", "Supervisor", 15_000L, true, true, false));
            repository.save(role("cashier", "Cashier", 10_000L, true, true, false));
            repository.save(role("baker", "Baker", 10_000L, true, true, false));
            repository.save(role("inactive_cashier", "Inactive Cashier", 5_000L, false, true, false));
            repository.save(role("auditor", "Auditor", 5_000L, true, false, false));
            repository.save(role("owner", "Owner", 5_000L, true, true, true));
            repository.flush();
            entityManager.clear();

            List<Role> result = repository.findAssignableRolesForActorRank(20_000L);

            assertThat(result)
                    .extracting(Role::getCode)
                    .containsExactly("SUPERVISOR", "BAKER", "CASHIER");
        }
    }

    @Nested
    @DisplayName("findActiveRoleCodesByUserId")
    class FindActiveRoleCodesByUserIdTests {

        @Test
        @DisplayName("Should return only active role codes assigned to the requested user")
        void shouldReturnOnlyActiveRoleCodesForRequestedUser() {
            UUID userId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();

            Role admin = repository.save(role("admin", "Admin", true));
            Role cashier = repository.save(role("cashier", "Cashier", true));
            Role manager = repository.save(role("manager", "Manager", false));
            repository.flush();

            entityManager.persist(userRole(userId, admin.getId()));
            entityManager.persist(userRole(userId, cashier.getId()));
            entityManager.persist(userRole(userId, manager.getId()));
            entityManager.persist(userRole(otherUserId, admin.getId()));
            entityManager.flush();
            entityManager.clear();

            List<String> result = repository.findActiveRoleCodesByUserId(userId);

            assertThat(result).containsExactlyInAnyOrder("ADMIN", "CASHIER");
        }
    }

    @Nested
    @DisplayName("findHighestActiveRankByUserId")
    class FindHighestActiveRankByUserIdTests {

        @Test
        @DisplayName("Should return the highest active rank assigned to the requested user")
        void shouldReturnHighestActiveRankAssignedToUser() {
            UUID userId = UUID.randomUUID();
            UUID otherUserId = UUID.randomUUID();

            Role cashier = repository.save(role("cashier", "Cashier", 10_000L, true, true, false));
            Role manager = repository.save(role("manager", "Manager", 20_000L, true, true, false));
            Role owner = repository.save(role("owner", "Owner", 30_000L, false, true, true));
            repository.flush();

            entityManager.persist(userRole(userId, cashier.getId()));
            entityManager.persist(userRole(userId, manager.getId()));
            entityManager.persist(userRole(userId, owner.getId()));
            entityManager.persist(userRole(otherUserId, cashier.getId()));
            entityManager.flush();
            entityManager.clear();

            long result = repository.findHighestActiveRankByUserId(userId);

            assertThat(result).isEqualTo(20_000L);
        }

        @Test
        @DisplayName("Should return zero when the user has no active roles")
        void shouldReturnZeroWhenUserHasNoActiveRoles() {
            UUID userId = UUID.randomUUID();

            Role inactiveOwner = repository.save(role("inactive_owner", "Inactive Owner", 30_000L, false, true, true));
            repository.flush();

            entityManager.persist(userRole(userId, inactiveOwner.getId()));
            entityManager.flush();
            entityManager.clear();

            long result = repository.findHighestActiveRankByUserId(userId);

            assertThat(result).isZero();
        }
    }

    @Nested
    @DisplayName("userHasProtectedActiveRole")
    class UserHasProtectedActiveRoleTests {

        @Test
        @DisplayName("Should return true when the user has an active protected role")
        void shouldReturnTrueWhenUserHasActiveProtectedRole() {
            UUID userId = UUID.randomUUID();

            Role protectedRole = repository.save(role("owner", "Owner", 30_000L, true, true, true));
            Role unprotectedRole = repository.save(role("cashier", "Cashier", 10_000L, true, true, false));
            repository.flush();

            entityManager.persist(userRole(userId, protectedRole.getId()));
            entityManager.persist(userRole(userId, unprotectedRole.getId()));
            entityManager.flush();
            entityManager.clear();

            boolean result = repository.userHasProtectedActiveRole(userId);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when the user has no active protected roles")
        void shouldReturnFalseWhenUserHasNoActiveProtectedRoles() {
            UUID userId = UUID.randomUUID();

            Role inactiveProtectedRole = repository.save(role("inactive_owner", "Inactive Owner", 30_000L, false, true, true));
            Role unprotectedRole = repository.save(role("cashier", "Cashier", 10_000L, true, true, false));
            repository.flush();

            entityManager.persist(userRole(userId, inactiveProtectedRole.getId()));
            entityManager.persist(userRole(userId, unprotectedRole.getId()));
            entityManager.flush();
            entityManager.clear();

            boolean result = repository.userHasProtectedActiveRole(userId);

            assertThat(result).isFalse();
        }
    }

    private Role role(String code, String name, boolean isActive) {
        return role(code, name, 10_000L, isActive, true, false);
    }

    private Role role(String code, String name, long rank, boolean isActive, boolean assignable, boolean protectedRole) {
        return Role.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .description(name + " role")
                .rank(rank)
                .isActive(isActive)
                .assignable(assignable)
                .protectedRole(protectedRole)
                .build();
    }

    private UserRole userRole(UUID userId, UUID roleId) {
        return UserRole.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .roleId(roleId)
                .build();
    }
}
