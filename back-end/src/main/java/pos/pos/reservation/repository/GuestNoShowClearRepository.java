package pos.pos.reservation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.reservation.entity.GuestNoShowClear;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface GuestNoShowClearRepository extends JpaRepository<GuestNoShowClear, UUID> {

    @Query("""
            select c from GuestNoShowClear c
            where c.restaurantId = :restaurantId
              and (c.customerId in :customerIds or c.contactPhone in :phones or c.contactEmail in :emails)
            """)
    List<GuestNoShowClear> findForGuests(
            @Param("restaurantId") UUID restaurantId,
            @Param("customerIds") Collection<UUID> customerIds,
            @Param("phones") Collection<String> phones,
            @Param("emails") Collection<String> emails
    );
}
