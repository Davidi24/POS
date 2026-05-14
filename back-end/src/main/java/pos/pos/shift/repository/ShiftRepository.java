package pos.pos.shift.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.shift.entity.Shift;

import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {
}
