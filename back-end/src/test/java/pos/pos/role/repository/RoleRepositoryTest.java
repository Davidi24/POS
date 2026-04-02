package pos.pos.role.repository;

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

    private Role role(String code, String name, boolean isActive) {
        return Role.builder()
                .id(UUID.randomUUID())
                .code(code)
                .name(name)
                .description(name + " role")
                .isActive(isActive)
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
