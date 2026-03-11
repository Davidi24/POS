package pos.pos.role.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import com.github.f4b6a3.uuid.UuidCreator;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public Role createRole(String name, String description) {

        Role role = Role.builder()
                .id(UuidCreator.getTimeOrdered())
                .name(name)
                .description(description)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        return roleRepository.save(role);
    }

}