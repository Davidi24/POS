package pos.pos.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.report.entity.ReportExecution;

import java.util.UUID;

public interface ReportExecutionRepository extends JpaRepository<ReportExecution, UUID> {
}
