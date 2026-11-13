package pos.pos.audit.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.audit.entity.AuditLog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // One entity type's entries for a restaurant (Inventory → History's item changes), newest first; every filter but the
    // time span is optional.
    @Query("""
            SELECT a FROM AuditLog a LEFT JOIN FETCH a.actorUser
            WHERE a.restaurant.id = :restaurantId AND a.entityType = :entityType
              AND (:entityId IS NULL OR a.entityId = :entityId)
              AND (:actorId IS NULL OR a.actorUser.id = :actorId)
              AND a.createdAt >= :from AND a.createdAt < :to
            ORDER BY a.createdAt DESC
            """)
    List<AuditLog> search(@Param("restaurantId") UUID restaurantId, @Param("entityType") String entityType, @Param("entityId") UUID entityId,
                          @Param("actorId") UUID actorId, @Param("from") OffsetDateTime from, @Param("to") OffsetDateTime to, Pageable pageable);

    // Who made those entries: [user id, first name, last name, username, count].
    @Query("""
            SELECT u.id, u.firstName, u.lastName, u.username, COUNT(a) FROM AuditLog a JOIN a.actorUser u
            WHERE a.restaurant.id = :restaurantId AND a.entityType = :entityType AND (:entityId IS NULL OR a.entityId = :entityId)
            GROUP BY u.id, u.firstName, u.lastName, u.username
            """)
    List<Object[]> people(@Param("restaurantId") UUID restaurantId, @Param("entityType") String entityType, @Param("entityId") UUID entityId);
}
