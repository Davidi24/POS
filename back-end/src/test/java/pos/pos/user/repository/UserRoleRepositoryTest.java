package pos.pos.user.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.support.AuthTestDataFactory;
import pos.pos.user.entity.UserRole;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRoleRepositoryTest {

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Test
    void findByUserId_shouldReturnMatchingRoles() {
        UUID userId = UUID.randomUUID();
        userRoleRepository.save(AuthTestDataFactory.userRole(userId, UUID.randomUUID()));
        userRoleRepository.save(AuthTestDataFactory.userRole(userId, UUID.randomUUID()));
        userRoleRepository.save(AuthTestDataFactory.userRole(UUID.randomUUID(), UUID.randomUUID()));

        List<UserRole> result = userRoleRepository.findByUserId(userId);

        assertEquals(2, result.size());
    }
}
