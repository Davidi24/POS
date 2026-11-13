package pos.pos.restaurant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * The private code an applicant uses to check their restaurant application, such as {@code 7KQ2M-X9D4T-PW3HB-R8N6C}:
 * 20 characters from an alphabet without look-alikes (100 random bits). Only its peppered SHA-256 hash is stored.
 */
@Component
public class RestaurantApplicationCodes {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int LENGTH = 20;
    private static final int GROUP = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String pepper;

    public RestaurantApplicationCodes(
            @Value("${app.restaurants.applications.code-pepper:${app.security.refresh-token.pepper}}") String pepper
    ) {
        if (pepper == null || pepper.length() < 16) {
            throw new IllegalStateException("The restaurant application code pepper must be at least 16 characters");
        }
        this.pepper = pepper;
    }

    /** A new code, grouped in fives for reading it out or typing it. */
    public String newCode() {
        StringBuilder code = new StringBuilder(LENGTH + LENGTH / GROUP);
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % GROUP == 0) {
                code.append('-');
            }
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /** The stored hash of [code], or null when it can't be a code (so it is simply not found). */
    public String hash(String code) {
        String normalized = normalize(code);
        if (normalized == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((pepper + "\u0000restaurant-application\u0000" + normalized).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash application code", ex);
        }
    }

    /**
     * Upper case without spaces or dashes; the letters people mix up are read as the digits they look like
     * (O as 0, I and L as 1). Null when what's left isn't 20 characters of the alphabet.
     */
    static String normalize(String code) {
        if (code == null) {
            return null;
        }
        StringBuilder normalized = new StringBuilder(LENGTH);
        for (char c : code.toUpperCase(Locale.ROOT).toCharArray()) {
            if (c == ' ' || c == '-' || c == '\t') {
                continue;
            }
            char read = switch (c) {
                case 'O' -> '0';
                case 'I', 'L' -> '1';
                default -> c;
            };
            if (new String(ALPHABET).indexOf(read) < 0 || normalized.length() == LENGTH) {
                return null;
            }
            normalized.append(read);
        }
        return normalized.length() == LENGTH ? normalized.toString() : null;
    }
}
