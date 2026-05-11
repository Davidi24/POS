package pos.pos.tables.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.tables.entity.TableCategory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableCategoryRepository extends JpaRepository<TableCategory, UUID> {

    List<TableCategory> findAllByBranch_IdOrderByDisplayOrderAscNameAsc(UUID branchId);

    Optional<TableCategory> findByIdAndBranch_Id(UUID categoryId, UUID branchId);
}
