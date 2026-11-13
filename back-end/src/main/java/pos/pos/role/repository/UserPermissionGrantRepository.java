package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.role.entity.UserPermissionGrant;

import java.util.List;
import java.util.UUID;

public interface UserPermissionGrantRepository extends JpaRepository<UserPermissionGrant, UUID> {

    List<UserPermissionGrant> findAllByUserId(UUID userId);

    @Modifying
    @Query("delete from UserPermissionGrant g where g.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
