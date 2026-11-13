package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.role.entity.UserPermissionDenial;

import java.util.List;
import java.util.UUID;

public interface UserPermissionDenialRepository extends JpaRepository<UserPermissionDenial, UUID> {

    List<UserPermissionDenial> findAllByUserId(UUID userId);

    @Modifying
    @Query("delete from UserPermissionDenial d where d.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
