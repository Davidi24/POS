package pos.pos.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.report.entity.ReportDefinition;

import java.util.UUID;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, UUID> {
}
