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
    @DisplayName("should reject active restaurants without an owner")
    void shouldRejectActiveRestaurantsWithoutAnOwner() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO restaurants (
                    id, name, legal_name, code, slug, currency, timezone, is_active, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
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
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("should allow pending restaurants without an owner")
    void shouldAllowPendingRestaurantsWithoutAnOwner() {
        int inserted = jdbcTemplate.update(
                """
                INSERT INTO restaurants (
                    id, name, legal_name, code, slug, currency, timezone, is_active, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
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
