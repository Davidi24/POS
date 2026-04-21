package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pos.pos.auth.dto.CurrentUserResponse;
import pos.pos.auth.mapper.CurrentUserMapper;
import pos.pos.auth.service.AuthProfileService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthProfileService")
class AuthProfileServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    private final AuthProfileService authProfileService = new AuthProfileService(new CurrentUserMapper());

    @Test
    @DisplayName("Should map authenticated user fields and split roles from permissions")
    void shouldMapAuthenticatedUserFieldsAndSplitRolesFromPermissions() {
        AuthenticatedUser user = AuthenticatedUser.builder()
                .id(USER_ID)
                .email("owner@pos.local")
                .username("owner.main")
                .firstName("Olivia")
                .lastName("Owner")
                .phone("+49-555-0202")
                .active(true)
                .emailVerified(true)
                .phoneVerified(false)
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(
                                new SimpleGrantedAuthority("ROLE_OWNER"),
                                new SimpleGrantedAuthority("ROLE_MANAGER"),
                                new SimpleGrantedAuthority("USERS_CREATE"),
                                new SimpleGrantedAuthority("SESSIONS_MANAGE")
                        )
                );

        CurrentUserResponse response = authProfileService.getMe(authentication);

        assertThat(response.getId()).isEqualTo(USER_ID);
        assertThat(response.getEmail()).isEqualTo("owner@pos.local");
        assertThat(response.getUsername()).isEqualTo("owner.main");
        assertThat(response.getFirstName()).isEqualTo("Olivia");
        assertThat(response.getLastName()).isEqualTo("Owner");
        assertThat(response.getPhone()).isEqualTo("+49-555-0202");
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.isEmailVerified()).isTrue();
        assertThat(response.isPhoneVerified()).isFalse();
        assertThat(response.getRoles()).containsExactly("OWNER", "MANAGER");
        assertThat(response.getPermissions()).containsExactly("USERS_CREATE", "SESSIONS_MANAGE");
    }

    @Test
    @DisplayName("Should return empty role and permission lists when authentication has no authorities")
    void shouldReturnEmptyRoleAndPermissionListsWhenAuthenticationHasNoAuthorities() {
        AuthenticatedUser user = AuthenticatedUser.builder()
                .id(USER_ID)
                .email("cashier@pos.local")
                .username("cashier.one")
                .firstName("Casey")
                .lastName("Cashier")
                .phone("+49-555-0303")
                .active(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());

        CurrentUserResponse response = authProfileService.getMe(authentication);

        assertThat(response.getRoles()).isEmpty();
        assertThat(response.getPermissions()).isEmpty();
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.isEmailVerified()).isFalse();
        assertThat(response.isPhoneVerified()).isFalse();
    }
}
