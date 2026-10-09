package pos.pos.unit.security.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.security.rbac.AppPermission;
import pos.pos.security.rbac.AppRole;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppRole")
class AppRoleTest {

    @Test
    @DisplayName("Should resolve roles by code and return null for unknown values")
    void shouldResolveFromCode() {
        assertThat(AppRole.fromCode("SUPER_ADMIN")).isEqualTo(AppRole.SUPER_ADMIN);
        assertThat(AppRole.fromCode("WAITER")).isEqualTo(AppRole.WAITER);
        assertThat(AppRole.fromCode("KITCHEN")).isEqualTo(AppRole.KITCHEN);
        assertThat(AppRole.fromCode("VIEWER")).isEqualTo(AppRole.VIEWER);
        assertThat(AppRole.fromCode("missing")).isNull();
    }

    @Test
    @DisplayName("Should keep rank ordering from highest privilege to lowest privilege")
    void shouldKeepRankOrdering() {
        assertThat(AppRole.SUPER_ADMIN.rank()).isGreaterThan(AppRole.OWNER.rank());
        assertThat(AppRole.OWNER.rank()).isGreaterThan(AppRole.ADMIN.rank());
        assertThat(AppRole.ADMIN.rank()).isGreaterThan(AppRole.WAITER.rank());
        assertThat(AppRole.WAITER.rank()).isGreaterThan(AppRole.VIEWER.rank());
        assertThat(AppRole.VIEWER.rank()).isGreaterThan(AppRole.KITCHEN.rank());
    }

    @Test
    @DisplayName("Should expose the expected permission sets")
    void shouldExposeExpectedPermissions() {
        assertThat(AppRole.SUPER_ADMIN.permissions()).containsExactlyInAnyOrder(AppPermission.values());
        assertThat(AppRole.OWNER.permissions()).containsExactlyInAnyOrder(AppPermission.values());
        assertThat(AppRole.ADMIN.permissions()).contains(
                AppPermission.MENUS_CREATE,
                AppPermission.MENUS_READ,
                AppPermission.MENUS_UPDATE,
                AppPermission.MENUS_DELETE
        );
        assertThat(AppRole.ADMIN.permissions()).contains(
                AppPermission.KDS_READ,
                AppPermission.KDS_UPDATE
        );
        assertThat(AppRole.MANAGER.permissions()).contains(
                AppPermission.USERS_CREATE,
                AppPermission.ROLES_READ,
                AppPermission.MENUS_CREATE,
                AppPermission.MENUS_READ,
                AppPermission.MENUS_UPDATE,
                AppPermission.ORDER_READ,
                AppPermission.ORDER_CLOSE,
                AppPermission.KDS_READ,
                AppPermission.KDS_UPDATE
        );
        assertThat(AppRole.MANAGER.permissions()).doesNotContain(
                AppPermission.USERS_DELETE,
                AppPermission.MENUS_DELETE
        );
        assertThat(AppRole.WAITER.permissions()).containsExactlyInAnyOrder(
                AppPermission.MENUS_READ,
                AppPermission.RESERVATION_READ,
                AppPermission.RESERVATION_MANAGE,
                AppPermission.SHIFT_SELF,
                AppPermission.KDS_READ,
                AppPermission.ORDER_READ,
                AppPermission.ORDER_CREATE,
                AppPermission.ORDER_UPDATE,
                AppPermission.ORDER_CLOSE,
                AppPermission.ORDER_DISCOUNT_APPLY,
                AppPermission.ORDER_TRANSFER,
                AppPermission.POS_ACCESS
        );
        assertThat(AppRole.MANAGER.permissions()).contains(AppPermission.PAYMENT_REFUND)
                .doesNotContain(AppPermission.REPORTS_READ, AppPermission.FRAUD_READ, AppPermission.FRAUD_REVIEW);
        assertThat(AppRole.ADMIN.permissions()).contains(AppPermission.PAYMENT_REFUND, AppPermission.REPORTS_READ)
                .doesNotContain(AppPermission.FRAUD_READ, AppPermission.FRAUD_REVIEW);
        assertThat(AppRole.CO_OWNER.permissions()).contains(AppPermission.PAYMENT_REFUND, AppPermission.REPORTS_READ,
                AppPermission.FRAUD_READ, AppPermission.FRAUD_REVIEW);
        assertThat(AppRole.WAITER.permissions()).doesNotContain(AppPermission.PAYMENT_REFUND, AppPermission.ORDER_VOID);
        assertThat(AppRole.KITCHEN.permissions()).containsExactlyInAnyOrder(
                AppPermission.MENUS_READ,
                AppPermission.ORDER_READ,
                AppPermission.KDS_READ,
                AppPermission.KDS_UPDATE,
                AppPermission.KDS_ACCESS
        );
    }

    @Test
    @DisplayName("Should open only the workspaces each role is meant to use")
    void shouldGrantWorkspaceAccessPerRole() {
        Set<AppPermission> staffWorkspaces = EnumSet.of(AppPermission.POS_ACCESS, AppPermission.KDS_ACCESS, AppPermission.ADMIN_ACCESS);
        Set<AppPermission> allWorkspaces = EnumSet.of(AppPermission.POS_ACCESS, AppPermission.KDS_ACCESS, AppPermission.ADMIN_ACCESS,
                AppPermission.STATISTICS_ACCESS, AppPermission.FRAUD_ACCESS);

        assertThat(workspaces(AppRole.OWNER)).isEqualTo(allWorkspaces);
        assertThat(workspaces(AppRole.CO_OWNER)).isEqualTo(allWorkspaces);
        assertThat(workspaces(AppRole.ADMIN)).containsExactlyInAnyOrder(AppPermission.POS_ACCESS, AppPermission.KDS_ACCESS,
                AppPermission.ADMIN_ACCESS, AppPermission.STATISTICS_ACCESS);
        assertThat(workspaces(AppRole.MANAGER)).containsExactlyInAnyOrder(AppPermission.POS_ACCESS, AppPermission.ADMIN_ACCESS);
        assertThat(workspaces(AppRole.WAITER)).containsExactly(AppPermission.POS_ACCESS);
        assertThat(workspaces(AppRole.VIEWER)).isEqualTo(staffWorkspaces);
        assertThat(workspaces(AppRole.KITCHEN)).containsExactly(AppPermission.KDS_ACCESS);
    }

    @Test
    @DisplayName("Should give Viewer only read permissions besides workspace access")
    void shouldKeepViewerReadOnly() {
        assertThat(AppRole.VIEWER.permissions())
                .allMatch(permission -> permission.name().endsWith("_READ") || permission.name().endsWith("_ACCESS"));
        assertThat(AppRole.VIEWER.assignable()).isTrue();
    }

    @Test
    @DisplayName("Should let Owner and Co-Owner hand out Admin and Manager, and Admins hand out Viewer")
    void shouldSetWhoHandsOutEachRole() {
        assertThat(AppRole.ADMIN.assignable()).isTrue();
        assertThat(AppRole.ADMIN.lowestManagingRole()).isNull();
        assertThat(AppRole.MANAGER.lowestManagingRole()).isEqualTo(AppRole.CO_OWNER);
        assertThat(AppRole.VIEWER.lowestManagingRole()).isEqualTo(AppRole.ADMIN);
    }

    private Set<AppPermission> workspaces(AppRole role) {
        Set<AppPermission> workspaces = EnumSet.noneOf(AppPermission.class);
        role.permissions().stream()
                .filter(permission -> permission.name().endsWith("_ACCESS"))
                .forEach(workspaces::add);
        return workspaces;
    }
}
