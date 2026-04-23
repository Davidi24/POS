package pos.pos.restaurant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.enums.BranchStatus;

import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndCodeAndDeletedAtIsNull(UUID restaurantId, String code);

    boolean existsByRestaurantIdAndCodeAndIdNotAndDeletedAtIsNull(UUID restaurantId, String code, UUID id);

    @Query(
            value = """
            SELECT b
            FROM Branch b
            WHERE b.deletedAt IS NULL
              AND b.restaurant.id = :restaurantId
              AND (:active IS NULL OR b.isActive = :active)
              AND (:status IS NULL OR b.status = :status)
              AND (:managerUserId IS NULL OR b.managerUserId = :managerUserId)
              AND (
                    :searchLike IS NULL
                    OR lower(b.name) LIKE :searchLike
                    OR lower(b.code) LIKE :searchLike
                    OR lower(b.email) LIKE :searchLike
                    OR lower(b.phone) LIKE :searchLike
                    OR lower(b.description) LIKE :searchLike
              )
            """,
            countQuery = """
            SELECT COUNT(b)
            FROM Branch b
            WHERE b.deletedAt IS NULL
              AND b.restaurant.id = :restaurantId
              AND (:active IS NULL OR b.isActive = :active)
              AND (:status IS NULL OR b.status = :status)
              AND (:managerUserId IS NULL OR b.managerUserId = :managerUserId)
              AND (
                    :searchLike IS NULL
                    OR lower(b.name) LIKE :searchLike
                    OR lower(b.code) LIKE :searchLike
                    OR lower(b.email) LIKE :searchLike
                    OR lower(b.phone) LIKE :searchLike
                    OR lower(b.description) LIKE :searchLike
              )
            """
    )
    Page<Branch> searchRestaurantBranches(
            UUID restaurantId,
            Boolean active,
            BranchStatus status,
            UUID managerUserId,
            String searchLike,
            Pageable pageable
    );
}
