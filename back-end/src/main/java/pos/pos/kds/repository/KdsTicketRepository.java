package pos.pos.kds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.kds.entity.KdsTicket;

import java.util.UUID;

public interface KdsTicketRepository extends JpaRepository<KdsTicket, UUID> {
}
