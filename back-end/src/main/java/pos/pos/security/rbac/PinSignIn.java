package pos.pos.security.rbac;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import pos.pos.auth.enums.SessionType;
import pos.pos.exception.auth.AuthException;

import java.util.ArrayList;
import java.util.List;

/**
 * Signed in with a PIN on a till, not with the password (user, 2026-11-25: "if someone logs in with PIN and not with password,
 * he should have limited things to do; things with a security risk"). Such a request carries [MARK] next to its permissions
 * (the JWT filter adds it, sign-in and refresh answer it in the person's permissions, so the app knows too), and what needs a
 * password checks it. For now: connecting devices (making a code, typing one, Use this device, agreeing to one).
 */
public final class PinSignIn {

    public static final String MARK = "PIN_SIGN_IN";

    /** What a PIN sign-in was refused, for the message (each written out below, so the app's translation table finds it). */
    public enum Need { CONNECT_DEVICES, YOUR_ACCOUNT, DOWNLOADS, CHANGE_SYSTEM }

    public static final Need CONNECT_DEVICES = Need.CONNECT_DEVICES;
    public static final Need YOUR_ACCOUNT = Need.YOUR_ACCOUNT;
    public static final Need DOWNLOADS = Need.DOWNLOADS;
    public static final Need CHANGE_SYSTEM = Need.CHANGE_SYSTEM;

    /** The 403 for [need]. */
    public static AuthException refusal(Need need) {
        return switch (need) {
            case CONNECT_DEVICES -> new AuthException("Sign in with your password to connect devices.", HttpStatus.FORBIDDEN);
            case YOUR_ACCOUNT -> new AuthException("Sign in with your password to change your account.", HttpStatus.FORBIDDEN);
            case DOWNLOADS -> new AuthException("Sign in with your password to download reports.", HttpStatus.FORBIDDEN);
            case CHANGE_SYSTEM -> new AuthException("Sign in with your password to do this.", HttpStatus.FORBIDDEN);
        };
    }

    /**
     * What a PIN sign-in doesn't get (user, 2026-11-25: "a PIN can do the job, a password can change the system"): devices;
     * staff, roles and permissions; money settings, salary, restaurant info, opening hours and the other settings; the audit
     * log, Fraud (Finance opens with SETTINGS_MONEY); editing the menu, prices and the floor plan; stock items, places,
     * suppliers, adjustments and approving counts; the Admin Hub itself; the platform's own. Service work stays: orders,
     * payments, voids, refunds, discounts, tables, bookings, sold-out, the kitchen display, My shift, receiving, waste,
     * transfers and counting stock, looking at Statistics. Reading permissions stay too (a PIN sees, it doesn't change).
     */
    public static final java.util.Set<String> PASSWORD_ONLY = java.util.Set.of(
            "TABLES_LAYOUT",
            "MENUS_CREATE", "MENUS_UPDATE", "MENUS_PRICES", "MENUS_DELETE",
            "INVENTORY_ADJUST", "INVENTORY_PLACES", "INVENTORY_ITEMS_CREATE", "INVENTORY_ITEMS_UPDATE", "INVENTORY_ITEMS_DELETE",
            "INVENTORY_COUNTS_APPROVE", "SUPPLIERS_UPDATE",
            "SHIFT_MANAGE", "SALARY_MANAGE",
            "USERS_CREATE", "USERS_UPDATE", "USERS_DELETE", "SESSIONS_MANAGE",
            "ROLES_CREATE", "ROLES_UPDATE", "ROLES_DELETE", "ROLES_ASSIGN_PERMISSIONS",
            "SETTINGS_UPDATE", "SETTINGS_MONEY", "SETTINGS_AUDIT",
            "DEVICES_CONNECT", "DEVICES_MANAGE",
            "RESTAURANTS_UPDATE", "HOURS_UPDATE",
            "FRAUD_ACCESS", "FRAUD_READ", "FRAUD_REVIEW",
            "ADMIN_ACCESS",
            "RESTAURANTS_CREATE", "RESTAURANTS_DELETE", "SETTINGS_EXPORT", "SETTINGS_IMPORT",
            "SETTINGS_TEMPLATE_MANAGE", "SETTINGS_TEMPLATE_APPLY", "REQUESTS_ASSIGN"
    );

    private PinSignIn() {
    }

    /** The permissions as the person gets them in this sign-in: a PIN one without [PASSWORD_ONLY] and with [MARK]. */
    public static List<String> mark(List<String> permissions, SessionType sessionType) {
        if (sessionType != SessionType.PIN) return permissions;
        List<String> marked = new ArrayList<>(permissions.stream().filter(code -> !PASSWORD_ONLY.contains(code)).toList());
        if (!marked.contains(MARK)) marked.add(MARK);
        return marked;
    }

    public static boolean is(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(a -> MARK.equals(a.getAuthority()));
    }

    /** 403 for a PIN sign-in. */
    public static void refuse(Authentication authentication, Need need) {
        if (is(authentication)) throw refusal(need);
    }

    /** 403 when the request in hand comes from a PIN sign-in. */
    public static void refuse(Need need) {
        refuse(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(), need);
    }
}
