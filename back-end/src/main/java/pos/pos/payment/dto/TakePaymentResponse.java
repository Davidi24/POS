package pos.pos.payment.dto;

/** The payment just taken and the order's bill after it. {@code orderClosed} is true when this payment closed it. */
public record TakePaymentResponse(
        PaymentResponse payment,
        OrderPaymentSummaryResponse summary,
        boolean orderClosed
) {
}
