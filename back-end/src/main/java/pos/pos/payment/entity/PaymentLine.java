package pos.pos.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A dish, or some of its portions, that one payment covered. */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLine {

    @Column(name = "order_line_item_id", nullable = false)
    private UUID orderLineItemId;

    @Column(name = "quantity", nullable = false)
    private int quantity;
}
