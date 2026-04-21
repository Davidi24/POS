package pos.pos.unit.auth.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.auth.dto.RegisterRequest;
import pos.pos.auth.mapper.AuthMapper;
import pos.pos.user.entity.User;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AuthMapper")
class AuthMapperTest {

    private final AuthMapper authMapper = new AuthMapper();

    @Test
    @DisplayName("Should map register request fields into a new user")
    void shouldMapRegisterRequestToUser() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("cashier@pos.local");
        request.setUsername("cashier.one");
        request.setFirstName("John");
        request.setLastName("Doe");

        OffsetDateTime before = OffsetDateTime.now();
        User user = authMapper.toUser(request, "hashed-password");
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(user.getEmail()).isEqualTo("cashier@pos.local");
        assertThat(user.getUsername()).isEqualTo("cashier.one");
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getCreatedAt()).isBetween(before, after);
        assertThat(user.getUpdatedAt()).isBetween(before, after);
    }
}
