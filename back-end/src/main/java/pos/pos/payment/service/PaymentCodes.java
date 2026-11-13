package pos.pos.payment.service;

import java.util.Locale;
import java.util.UUID;

/** Short codes printed for people. Built from random UUID bits so two tills never get the same one in a burst. */
public final class PaymentCodes {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private PaymentCodes() {
    }

    public static String paymentReference() {
        return "PAY-" + randomCode(10);
    }

    public static String receiptNumber(String prefix) {
        String clean = prefix == null ? "" : prefix.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (clean.isEmpty()) {
            clean = "INV";
        }
        if (clean.length() > 20) {
            clean = clean.substring(0, 20);
        }
        return clean + "-" + randomCode(10);
    }

    static String randomCode(int length) {
        UUID random = UUID.randomUUID();
        long bits = random.getMostSignificantBits() ^ Long.rotateLeft(random.getLeastSignificantBits(), 17);
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET[(int) (bits & 31)]);
            bits >>>= 5;
            if (bits == 0 && i < length - 1) {
                UUID more = UUID.randomUUID();
                bits = more.getMostSignificantBits() ^ more.getLeastSignificantBits();
            }
        }
        return code.toString();
    }
}
