package pos.pos.role.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.role.entity.Permission;
import pos.pos.role.repository.PermissionRepository;
import com.github.f4b6a3.uuid.UuidCreator;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public Permission createPermission(String name, String description) {

        Permission permission = Permission.builder()
                .id(UuidCreator.getTimeOrdered())
                .name(name)
                .description(description)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        return permissionRepository.save(permission);
    }

}