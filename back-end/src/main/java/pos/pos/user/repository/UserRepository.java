package pos.pos.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.user.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByNormalizedPhoneAndDeletedAtIsNull(String normalizedPhone);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    boolean existsByNormalizedPhoneAndDeletedAtIsNull(String normalizedPhone);

    @Query("""
        SELECT u
        FROM User u
        WHERE u.id = :userId
          AND u.deletedAt IS NULL
          AND u.isActive = true
    """)
    Optional<User> findActiveById(UUID userId);
}
