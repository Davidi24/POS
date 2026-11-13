package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.SupplierPlace;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierPlaceRepository extends JpaRepository<SupplierPlace, UUID> {

    List<SupplierPlace> findAllBySupplierIdInOrderByCreatedAtAsc(Collection<UUID> supplierIds);

    Optional<SupplierPlace> findByIdAndSupplierId(UUID id, UUID supplierId);
}
