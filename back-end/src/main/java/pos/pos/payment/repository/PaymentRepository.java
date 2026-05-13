package pos.pos.payment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.payment.entity.Payment;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
}
