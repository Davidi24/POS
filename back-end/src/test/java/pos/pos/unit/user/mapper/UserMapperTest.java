package pos.pos.unit.user.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserMapperTest {

    private final UserMapper userMapper = new UserMapper();

    @Nested
    @DisplayName("toUserResponse(User)")
    class ToUserResponseWithoutRolesTests {

        @Test
        @DisplayName("Should map user fields and use empty roles list")
        void shouldMapUserFieldsAndUseEmptyRolesList() {
            User user = user();

            UserResponse response = userMapper.toUserResponse(user);

            assertThat(response.getId()).isEqualTo(user.getId());
            assertThat(response.getEmail()).isEqualTo(user.getEmail());
            assertThat(response.getUsername()).isEqualTo(user.getUsername());
            assertThat(response.getFirstName()).isEqualTo(user.getFirstName());
            assertThat(response.getLastName()).isEqualTo(user.getLastName());
            assertThat(response.getPhone()).isEqualTo(user.getPhone());
            assertThat(response.getIsActive()).isEqualTo(user.isActive());
            assertThat(response.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("Should return null when user is null")
        void shouldReturnNullWhenUserIsNull() {
            UserResponse response = userMapper.toUserResponse((User) null);

            assertThat(response).isNull();
        }
    }

    @Nested
    @DisplayName("toUserResponse(User, roles)")
    class ToUserResponseWithRolesTests {

        @Test
        @DisplayName("Should map user fields and copy provided roles")
        void shouldMapUserFieldsAndCopyProvidedRoles() {
            User user = user();
            List<String> roles = new ArrayList<>(List.of("ADMIN", "CASHIER"));

            UserResponse response = userMapper.toUserResponse(user, roles);
            roles.add("MANAGER");

            assertThat(response.getRoles()).containsExactly("ADMIN", "CASHIER");
            assertThatThrownBy(() -> response.getRoles().add("OWNER"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should use empty roles list when roles are null")
        void shouldUseEmptyRolesListWhenRolesAreNull() {
            UserResponse response = userMapper.toUserResponse(user(), null);

            assertThat(response.getRoles()).isEmpty();
        }

        @Test
        @DisplayName("Should return null when user is null even if roles are provided")
        void shouldReturnNullWhenUserIsNullEvenIfRolesProvided() {
            UserResponse response = userMapper.toUserResponse(null, List.of("ADMIN"));

            assertThat(response).isNull();
        }
    }

    private User user() {
        return User.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .email("cashier@pos.local")
                .username("cashier.one")
                .firstName("John")
                .lastName("Doe")
                .phone("+49123456789")
                .isActive(true)
                .passwordHash("stored-hash")
                .build();
    }
}
