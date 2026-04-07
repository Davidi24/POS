package pos.pos.unit.security.rback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.security.rbac.AppRole;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppRole")
class AppRoleTest {

    @Test
    @DisplayName("Should derive rank from enum order")
    void shouldDeriveRankFromEnumOrder() {
        AppRole[] roles = AppRole.values();

        for (int i = 0; i < roles.length; i++) {
            long expectedRank = (long) (roles.length - i) * 10_000L;

            assertThat(roles[i].rank()).isEqualTo(expectedRank);
        }

        assertThat(AppRole.SUPER_ADMIN.rank()).isGreaterThan(AppRole.ADMIN.rank());
        assertThat(AppRole.ADMIN.rank()).isGreaterThan(AppRole.MANAGER.rank());
        assertThat(AppRole.MANAGER.rank()).isGreaterThan(AppRole.CASHIER.rank());
    }
}
