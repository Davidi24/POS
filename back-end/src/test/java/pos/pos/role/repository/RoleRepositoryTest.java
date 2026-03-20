package pos.pos.role.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.support.AuthTestDataFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoleRepositoryTest {

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void findByIdIn_shouldReturnRequestedRolesOnly() {
        var admin = roleRepository.save(AuthTestDataFactory.role("ADMIN_" + System.nanoTime()));
        var staff = roleRepository.save(AuthTestDataFactory.role("STAFF_" + System.nanoTime()));
        roleRepository.save(AuthTestDataFactory.role("CHEF_" + System.nanoTime()));

        List<?> result = roleRepository.findByIdIn(List.of(admin.getId(), staff.getId()));

        assertEquals(2, result.size());
    }
}
