package pos.pos.unit.restaurant.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pos.pos.support.AbstractTestProfilePostgresTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Restaurant schema constraints")
class RestaurantRepositoryTest extends AbstractTestProfilePostgresTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("should allow an active restaurant while owner setup is pending")
    void shouldAllowActiveRestaurantsWithoutAnOwner() {
        assertThat(jdbcTemplate.update(
                """
                INSERT INTO restaurants (
                    id, name, legal_name, code, slug, currency, timezone, is_active, status, display_order, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, now(), now())
                """,
                UUID.randomUUID(),
                "Ownerless Restaurant",
                "Ownerless Restaurant LLC",
                "OWNERLESS_RESTAURANT",
                "ownerless-restaurant",
                "USD",
                "Europe/Berlin",
                true,
                "ACTIVE"
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("should allow pending restaurants without an owner")
    void shouldAllowPendingRestaurantsWithoutAnOwner() {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO restaurants (
                    id, name, legal_name, code, slug, currency, timezone, is_active, status, display_order, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, now(), now())
                """,
                UUID.randomUUID(),
                "Pending Restaurant",
                "Pending Restaurant LLC",
                "PENDING_RESTAURANT",
                "pending-restaurant",
                "USD",
                "Europe/Berlin",
                false,
                "PENDING"
        );

        assertThat(inserted).isEqualTo(1);
    }
}
