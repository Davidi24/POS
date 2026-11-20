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
        // Admin (2026-11-19): Admin Hub (shifts, users, settings, audit log), Inventory with suppliers and Statistics;
        // POS and the kitchen display come from the roles under it, and the owner-level parts stay with the owner.
        assertThat(AppRole.ADMIN.permissions()).contains(
                AppPermission.SHIFT_MANAGE,
                AppPermission.USERS_CREATE,
                AppPermission.SETTINGS_UPDATE,
                AppPermission.SETTINGS_AUDIT,
                AppPermission.INVENTORY_RECEIVE,
                AppPermission.INVENTORY_ITEMS_DELETE,
                AppPermission.INVENTORY_COUNTS_APPROVE,
                AppPermission.SUPPLIERS_UPDATE,
                AppPermission.REPORTS_READ
        );
        assertThat(AppRole.ADMIN.permissions()).doesNotContain(
                AppPermission.POS_ACCESS,
                AppPermission.KDS_ACCESS,
                AppPermission.MENUS_CREATE,
                AppPermission.SALARY_MANAGE,
                AppPermission.DEVICES_MANAGE,
                AppPermission.SETTINGS_MONEY,
                AppPermission.RESTAURANTS_UPDATE,
                AppPermission.ROLES_UPDATE,
                AppPermission.FRAUD_ACCESS,
                // Finance shows wages and money out: the owner's unless they give it.
                AppPermission.STATS_FINANCE_READ
        );
        assertThat(AppRole.MANAGER.permissions()).contains(
                AppPermission.USERS_CREATE,
                AppPermission.ROLES_READ,
                AppPermission.MENUS_CREATE,
                AppPermission.MENUS_READ,
                AppPermission.MENUS_UPDATE,
                AppPermission.ORDER_READ,
                AppPermission.ORDER_CLOSE,
                AppPermission.SUPPLIERS_READ
        );
        // No Kitchen display permissions without opening it (V126), and no Salary (its own permission since 2026-11-19).
        assertThat(AppRole.MANAGER.permissions()).doesNotContain(
                AppPermission.USERS_DELETE,
                AppPermission.MENUS_DELETE,
                AppPermission.KDS_READ,
                AppPermission.KDS_UPDATE,
                AppPermission.SALARY_MANAGE
        );
        // Each built-in role holds only its own workspace's permissions: the waiter POS, the kitchen the KDS.
        assertThat(AppRole.WAITER.permissions()).containsExactlyInAnyOrder(
                AppPermission.TABLES_READ,
                AppPermission.TABLES_UPDATE,
                AppPermission.TABLES_PAYMENT,
                AppPermission.ORDER_READ,
                AppPermission.ORDER_UPDATE,
                AppPermission.ORDER_CREATE,
                AppPermission.ORDER_CANCEL,
                AppPermission.ORDER_CLOSE,
                AppPermission.ORDER_TRANSFER,
                AppPermission.ORDER_DISCOUNT_APPLY,
                AppPermission.KITCHEN_STATUS_READ,
                AppPermission.ORDER_KITCHEN_READ,
                AppPermission.RESERVATION_READ,
                AppPermission.RESERVATION_MANAGE,
                AppPermission.RESERVATION_CANCEL,
                AppPermission.MENUS_READ,
                AppPermission.MENUS_SOLD_OUT,
                AppPermission.SALES_SELF,
                AppPermission.ORDER_HISTORY_READ,
                AppPermission.ORDER_REOPEN_PAYMENT,
                AppPermission.SHIFT_SELF,
                AppPermission.SHIFT_PAY_SELF,
                AppPermission.SHIFT_TEAM,
                AppPermission.POS_ACCESS
        );
        assertThat(AppRole.MANAGER.permissions()).contains(AppPermission.PAYMENT_REFUND)
                .doesNotContain(AppPermission.REPORTS_READ, AppPermission.FRAUD_READ, AppPermission.FRAUD_REVIEW);
        // Refunds come with the Manager role an admin gets by default, not with Admin itself (2026-11-19).
        assertThat(AppRole.ADMIN.permissions()).contains(AppPermission.REPORTS_READ)
                .doesNotContain(AppPermission.PAYMENT_REFUND, AppPermission.FRAUD_READ, AppPermission.FRAUD_REVIEW);
        assertThat(AppRole.CO_OWNER.permissions()).contains(AppPermission.PAYMENT_REFUND, AppPermission.REPORTS_READ,
                AppPermission.STATS_FINANCE_READ, AppPermission.TABLES_LAYOUT, AppPermission.INVENTORY_COUNTS_APPROVE,
                AppPermission.FRAUD_READ, AppPermission.FRAUD_REVIEW);
        assertThat(AppRole.WAITER.permissions()).doesNotContain(AppPermission.PAYMENT_REFUND, AppPermission.ORDER_VOID);
        // The kitchen display, My shift, and Inventory's stock and items to look at (2026-11-19).
        assertThat(AppRole.KITCHEN.permissions()).containsExactlyInAnyOrder(
                AppPermission.KDS_READ,
                AppPermission.KDS_UPDATE,
                AppPermission.KDS_UPCOMING_READ,
                AppPermission.KDS_MENU_READ,
                AppPermission.KDS_HISTORY_READ,
                AppPermission.KDS_ACCESS,
                AppPermission.SHIFT_SELF,
                AppPermission.SHIFT_PAY_SELF,
                AppPermission.SHIFT_TEAM,
                AppPermission.INVENTORY_ACCESS,
                AppPermission.INVENTORY_STOCK_READ,
                AppPermission.INVENTORY_ITEMS_READ
        );
    }

    @Test
    @DisplayName("Should open only the workspaces each role is meant to use")
    void shouldGrantWorkspaceAccessPerRole() {
        Set<AppPermission> staffWorkspaces = EnumSet.of(AppPermission.POS_ACCESS, AppPermission.KDS_ACCESS, AppPermission.ADMIN_ACCESS,
                AppPermission.INVENTORY_ACCESS);
        Set<AppPermission> allWorkspaces = EnumSet.of(AppPermission.POS_ACCESS, AppPermission.KDS_ACCESS, AppPermission.ADMIN_ACCESS,
                AppPermission.STATISTICS_ACCESS, AppPermission.INVENTORY_ACCESS, AppPermission.FRAUD_ACCESS);

        assertThat(workspaces(AppRole.OWNER)).isEqualTo(allWorkspaces);
        assertThat(workspaces(AppRole.CO_OWNER)).isEqualTo(allWorkspaces);
        assertThat(workspaces(AppRole.ADMIN)).containsExactlyInAnyOrder(
                AppPermission.ADMIN_ACCESS, AppPermission.STATISTICS_ACCESS, AppPermission.INVENTORY_ACCESS);
        assertThat(workspaces(AppRole.MANAGER)).containsExactlyInAnyOrder(AppPermission.POS_ACCESS, AppPermission.ADMIN_ACCESS,
                AppPermission.INVENTORY_ACCESS);
        assertThat(workspaces(AppRole.WAITER)).containsExactly(AppPermission.POS_ACCESS);
        assertThat(workspaces(AppRole.VIEWER)).isEqualTo(staffWorkspaces);
        assertThat(workspaces(AppRole.KITCHEN)).containsExactlyInAnyOrder(AppPermission.KDS_ACCESS, AppPermission.INVENTORY_ACCESS);
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
