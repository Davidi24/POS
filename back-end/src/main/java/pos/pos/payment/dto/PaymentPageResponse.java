package pos.pos.payment.dto;

import java.util.List;

public record PaymentPageResponse(
        List<PaymentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
