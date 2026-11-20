package pos.pos.unit.security.rbac;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.security.rbac.AppPermission;
import pos.pos.security.rbac.AppRole;
import pos.pos.security.rbac.PermissionTree;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PermissionTree")
class PermissionTreeTest {

    @Test
    @DisplayName("Should place every permission and give every parent a real permission")
    void shouldPlaceEveryPermission() {
        Set<String> codes = Arrays.stream(AppPermission.values()).map(Enum::name).collect(Collectors.toSet());
        for (AppPermission permission : AppPermission.values()) {
            assertThat(PermissionTree.placeOf(permission.name())).as(permission.name()).isNotNull();
            String parent = PermissionTree.parentOf(permission.name());
            if (parent != null) assertThat(codes).as(permission.name() + " needs " + parent).contains(parent);
        }
        assertThat(PermissionTree.ordered()).containsExactlyInAnyOrderElementsOf(codes);
    }

    @Test
    @DisplayName("Should give each built-in role only permissions whose parents it has too")
    void shouldKeepBuiltInRolesWhole() {
        for (AppRole role : AppRole.values()) {
            List<String> codes = role.permissions().stream().map(Enum::name).toList();
            assertThat(PermissionTree.normalize(codes)).as(role.name()).containsExactlyInAnyOrderElementsOf(codes);
        }
    }

    @Test
    @DisplayName("Should drop what needs a place that's switched off")
    void shouldDropChildrenOfAnOffPlace() {
        assertThat(PermissionTree.normalize(List.of("POS_ACCESS", "TABLES_UPDATE", "ORDER_READ", "ORDER_UPDATE")))
                .containsExactlyInAnyOrder("POS_ACCESS", "ORDER_READ", "ORDER_UPDATE", "ORDER_CREATE", "ORDER_CANCEL");
        // Starting and cancelling an order come and go with "Edit orders".
        assertThat(PermissionTree.normalize(List.of("POS_ACCESS", "ORDER_READ", "ORDER_CREATE"))).containsExactly("POS_ACCESS", "ORDER_READ");
        assertThat(PermissionTree.normalize(List.of("INVENTORY_ACCESS", "INVENTORY_STOCK_READ", "INVENTORY_RECEIVE", "INVENTORY_ITEMS_DELETE")))
                .containsExactly("INVENTORY_ACCESS", "INVENTORY_STOCK_READ", "INVENTORY_RECEIVE");
        assertThat(PermissionTree.isPlatformOnly("SETTINGS_EXPORT")).isTrue();
        assertThat(PermissionTree.isPlatformOnly("SETTINGS_UPDATE")).isFalse();
        assertThat(PermissionTree.placeOf("SETTINGS_AUDIT").part()).isEqualTo("Audit log");
    }
}
