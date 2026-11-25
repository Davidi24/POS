package pos.pos.device.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Pairing codes a person types on the new till (user, 2026-11-25: "connect with the code"): 8 letters and digits shown as
 * "K7P4-9QXM", without the ones that look alike (0/O, 1/I/L). Typed in any case, with or without the dash or spaces. About
 * 40 bits, short-lived, one open per device and tries limited per network address, so they can't be guessed. Codes made
 * before were long random texts; those are still read as they are.
 */
public final class PairingCodes {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TYPED = Pattern.compile("^[A-Za-z0-9]{" + LENGTH + "}$");

    private PairingCodes() {
    }

    /** A new code as it's kept (hashed): 8 characters, no dash. */
    public static String newCode() {
        StringBuilder out = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return out.toString();
    }

    /** "K7P4-9QXM": how a code is shown, to read out and type. */
    public static String shown(String code) {
        return code.length() == LENGTH ? code.substring(0, 4) + "-" + code.substring(4) : code;
    }

    /** What was typed, as it was kept: a short code in capitals without dash or spaces; an older long code as it is. */
    public static String typed(String raw) {
        if (raw == null) return null;
        String compact = raw.replaceAll("[\\s-]", "");
        return TYPED.matcher(compact).matches() ? compact.toUpperCase(Locale.ROOT) : raw.trim();
    }
}
