package pos.pos.kds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.kds.entity.KdsStation;

import java.util.UUID;

public interface KdsStationRepository extends JpaRepository<KdsStation, UUID> {
}
