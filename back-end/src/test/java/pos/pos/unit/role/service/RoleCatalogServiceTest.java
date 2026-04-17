package pos.pos.unit.role.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.role.dto.RoleResponse;
import pos.pos.role.entity.Role;
import pos.pos.role.service.RoleCatalogService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleCatalogService")
class RoleCatalogServiceTest {

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @InjectMocks
    private RoleCatalogService roleCatalogService;

    @Test
    @DisplayName("Should map assignable roles to API responses")
    void shouldMapAssignableRolesToResponses() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(UUID.randomUUID())
                        .email("manager@pos.local")
                        .username("manager")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
        Role manager = Role.builder()
                .id(UUID.randomUUID())
                .code("MANAGER")
                .name("Manager")
                .description("Store manager")
                .rank(20_000L)
                .isSystem(false)
                .isActive(true)
                .assignable(true)
                .protectedRole(false)
                .build();
        Role waiter = Role.builder()
                .id(UUID.randomUUID())
                .code("WAITER")
                .name("Waiter")
                .description("Handles orders")
                .rank(10_000L)
                .isSystem(false)
                .isActive(true)
                .assignable(true)
                .protectedRole(false)
                .build();

        when(roleHierarchyService.getAssignableRoles(authentication)).thenReturn(List.of(manager, waiter));

        List<RoleResponse> responses = roleCatalogService.getAssignableRoles(authentication);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getCode()).isEqualTo("MANAGER");
        assertThat(responses.get(0).getDescription()).isEqualTo("Store manager");
        assertThat(responses.get(0).getIsAssignable()).isTrue();
        assertThat(responses.get(1).getCode()).isEqualTo("WAITER");
        assertThat(responses.get(1).getRank()).isEqualTo(10_000L);
        verify(roleHierarchyService).getAssignableRoles(authentication);
    }
}
