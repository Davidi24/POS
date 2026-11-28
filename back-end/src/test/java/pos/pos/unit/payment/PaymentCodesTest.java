package pos.pos.unit.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.payment.service.PaymentCodes;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentCodes")
class PaymentCodesTest {

    @Test
    @DisplayName("payment references are short, readable and don't repeat in a burst")
    void referencesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100_000; i++) {
            String reference = PaymentCodes.paymentReference();
            assertThat(reference).matches("PAY-[A-HJ-NP-Z2-9]{10}");
            assertThat(seen.add(reference)).as("repeat after %d", i).isTrue();
        }
    }

    @Test
    @DisplayName("receipt numbers use the cleaned invoice prefix, or INV when there is none")
    void receiptPrefix() {
        assertThat(PaymentCodes.receiptNumber("inv")).matches("INV-[A-HJ-NP-Z2-9]{10}");
        assertThat(PaymentCodes.receiptNumber(" bill/2026 ")).matches("BILL2026-[A-HJ-NP-Z2-9]{10}");
        assertThat(PaymentCodes.receiptNumber(null)).startsWith("INV-");
        assertThat(PaymentCodes.receiptNumber("@@@")).startsWith("INV-");
        assertThat(PaymentCodes.receiptNumber("X".repeat(80))).hasSize(31);
    }
}
