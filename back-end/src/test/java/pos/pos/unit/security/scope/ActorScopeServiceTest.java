package pos.pos.unit.security.scope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pos.pos.exception.auth.ActorScopeNotAvailableException;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.AppPermission;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActorScopeService")
class ActorScopeServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID DEFAULT_BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000031");

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @InjectMocks
    private ActorScopeService actorScopeService;

    @Test
    @DisplayName("resolve should build actor scope with tenant ids, roles and permissions")
    void shouldBuildActorScope() {
        Authentication authentication = authentication();
        User actor = actor();

        given(roleHierarchyService.currentUserId(authentication)).willReturn(USER_ID);
        given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(false);
        given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.of(actor));

        ActorScope scope = actorScopeService.resolve(authentication);

        assertThat(scope.userId()).isEqualTo(USER_ID);
        assertThat(scope.actor()).isEqualTo(actor);
        assertThat(scope.restaurantId()).isEqualTo(RESTAURANT_ID);
        assertThat(scope.defaultBranchId()).isEqualTo(DEFAULT_BRANCH_ID);
        assertThat(scope.superAdmin()).isFalse();
        assertThat(scope.hasRole("owner")).isTrue();
        assertThat(scope.hasPermission(AppPermission.USERS_UPDATE)).isTrue();
        assertThat(scope.hasPermission("users_update")).isTrue();
        assertThat(scope.belongsToRestaurant(RESTAURANT_ID)).isTrue();
        assertThat(scope.belongsToDefaultBranch(DEFAULT_BRANCH_ID)).isTrue();
    }

    @Test
    @DisplayName("resolve should reject authentication without a persisted actor")
    void shouldRejectMissingActor() {
        Authentication authentication = authentication();

        given(roleHierarchyService.currentUserId(authentication)).willReturn(USER_ID);
        given(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> actorScopeService.resolve(authentication))
                .isInstanceOf(ActorScopeNotAvailableException.class);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(USER_ID)
                        .email("owner@pos.local")
                        .username("owner")
                        .active(true)
                        .build(),
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_OWNER"),
                        new SimpleGrantedAuthority("USERS_READ"),
                        new SimpleGrantedAuthority("USERS_UPDATE")
                )
        );
    }

    private User actor() {
        return User.builder()
                .id(USER_ID)
                .email("owner@pos.local")
                .username("owner")
                .passwordHash("stored")
                .firstName("Olivia")
                .lastName("Owner")
                .status("ACTIVE")
                .isActive(true)
                .restaurantId(RESTAURANT_ID)
                .defaultBranchId(DEFAULT_BRANCH_ID)
                .build();
    }
}
