package pos.pos.menu.policy;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;

import java.math.BigDecimal;

/**
 * Changing a price is its own permission (user, 2026-11-20: "edit price is another permission"): "Change dishes and menus"
 * changes everything else, a different price needs MENUS_PRICES. Adding a dish with its price is "Add dishes and menus".
 */
public final class MenuPriceRule {

    private MenuPriceRule() {
    }

    public static void assertMayChange(Authentication auth, BigDecimal before, BigDecimal after) {
        if (same(before, after)) return;
        boolean allowed = auth != null && auth.getAuthorities().stream().anyMatch(a -> "MENUS_PRICES".equals(a.getAuthority()));
        if (!allowed) throw new AuthException("Changing prices needs “Change prices”.", HttpStatus.FORBIDDEN);
    }

    // No price is the same as 0.
    private static boolean same(BigDecimal a, BigDecimal b) {
        return (a == null ? BigDecimal.ZERO : a).compareTo(b == null ? BigDecimal.ZERO : b) == 0;
    }
}
