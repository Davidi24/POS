package pos.pos.payment.service;

import pos.pos.payment.enums.PaymentMethod;

@FunctionalInterface
public interface PaymentCapturePolicy {
    void requireCaptureAvailable(PaymentMethod method);
}
