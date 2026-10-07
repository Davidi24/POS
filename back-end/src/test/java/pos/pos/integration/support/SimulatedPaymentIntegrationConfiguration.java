package pos.pos.integration.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import pos.pos.payment.service.PaymentCapturePolicy;

@TestConfiguration(proxyBeanMethods = false)
public class SimulatedPaymentIntegrationConfiguration {

    @Bean
    @Primary
    PaymentCapturePolicy simulatedPaymentCapturePolicy() {
        return method -> { };
    }
}
