package pos.pos.menu.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.menu.entity.OptionGroupType;

import java.util.List;
import java.util.UUID;

public interface OptionGroupTypeRepository extends JpaRepository<OptionGroupType, UUID> {

    @Query("""
            SELECT type
            FROM OptionGroupType type
            ORDER BY type.name ASC, type.id ASC
            """)
    List<OptionGroupType> findAllOrdered();

    @Query("""
            SELECT type
            FROM OptionGroupType type
            WHERE lower(type.code) LIKE :search
               OR lower(type.name) LIKE :search
            ORDER BY type.name ASC, type.id ASC
            """)
    List<OptionGroupType> searchByCodeOrName(String search);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
