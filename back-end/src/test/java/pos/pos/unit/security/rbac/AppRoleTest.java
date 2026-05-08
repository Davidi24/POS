package pos.pos.unit.security.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.security.rbac.AppPermission;
import pos.pos.security.rbac.AppRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppRole")
class AppRoleTest {

    @Test
    @DisplayName("Should resolve roles by code and return null for unknown values")
    void shouldResolveFromCode() {
        assertThat(AppRole.fromCode("SUPER_ADMIN")).isEqualTo(AppRole.SUPER_ADMIN);
        assertThat(AppRole.fromCode("WAITER")).isEqualTo(AppRole.WAITER);
        assertThat(AppRole.fromCode("missing")).isNull();
    }

    @Test
    @DisplayName("Should keep rank ordering from highest privilege to lowest privilege")
    void shouldKeepRankOrdering() {
        assertThat(AppRole.SUPER_ADMIN.rank()).isGreaterThan(AppRole.OWNER.rank());
        assertThat(AppRole.OWNER.rank()).isGreaterThan(AppRole.ADMIN.rank());
        assertThat(AppRole.ADMIN.rank()).isGreaterThan(AppRole.WAITER.rank());
    }

    @Test
    @DisplayName("Should expose the expected permission sets")
    void shouldExposeExpectedPermissions() {
        assertThat(AppRole.SUPER_ADMIN.permissions()).containsExactlyInAnyOrder(AppPermission.values());
        assertThat(AppRole.OWNER.permissions()).containsExactlyInAnyOrder(AppPermission.values());
        assertThat(AppRole.MANAGER.permissions()).contains(
                AppPermission.USERS_CREATE,
                AppPermission.ROLES_READ,
                AppPermission.MENUS_READ,
                AppPermission.MENUS_UPDATE
        );
        assertThat(AppRole.MANAGER.permissions()).doesNotContain(AppPermission.USERS_DELETE);
        assertThat(AppRole.WAITER.permissions()).isEmpty();
    }
}
