//package pos.pos.auth.mapper;
//
//import org.junit.jupiter.api.Test;
//import pos.pos.auth.dto.RegisterRequest;
//import pos.pos.user.dto.UserResponse;
//import pos.pos.user.entity.User;
//
//import java.time.OffsetDateTime;
//import java.util.UUID;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class AuthMapperTest {
//
//    private final AuthMapper authMapper = new AuthMapper();
//
//    @Test
//    void toUser_shouldMapRegisterRequestToUser() {
//
//        RegisterRequest request = new RegisterRequest();
//        request.setEmail("test@test.com");
//        request.setPassword("Password123");
//        request.setFirstName("John");
//        request.setLastName("Doe");
//
//        String passwordHash = "hashedPassword";
//
//        User user = authMapper.toUser(request, passwordHash);
//
//        assertNotNull(user);
//        assertEquals(request.getEmail(), user.getEmail());
//        assertEquals(passwordHash, user.getPasswordHash());
//        assertEquals(request.getFirstName(), user.getFirstName());
//        assertEquals(request.getLastName(), user.getLastName());
//        assertNotNull(user.getCreatedAt());
//        assertNotNull(user.getUpdatedAt());
//    }
//
//    @Test
//    void toUser_shouldSetTimestamps() {
//
//        RegisterRequest request = new RegisterRequest();
//        request.setEmail("test@test.com");
//        request.setPassword("Password123");
//        request.setFirstName("John");
//        request.setLastName("Doe");
//
//        User user = authMapper.toUser(request, "hash");
//
//        OffsetDateTime createdAt = user.getCreatedAt();
//        OffsetDateTime updatedAt = user.getUpdatedAt();
//
//        assertNotNull(createdAt);
//        assertNotNull(updatedAt);
//    }
//
//    @Test
//    void toUserResponse_shouldMapUserToUserResponse() {
//
//        User user = new User();
//        user.setId(UUID.randomUUID());
//        user.setEmail("test@test.com");
//        user.setFirstName("John");
//        user.setLastName("Doe");
//        user.setPhone("+49123456789");
//        user.setIsActive(true);
//
//        UserResponse response = authMapper.toUserResponse(user);
//
//        assertNotNull(response);
//        assertEquals(user.getId(), response.getId());
//        assertEquals(user.getEmail(), response.getEmail());
//        assertEquals(user.getFirstName(), response.getFirstName());
//        assertEquals(user.getLastName(), response.getLastName());
//        assertEquals(user.getPhone(), response.getPhone());
//        assertEquals(user.getIsActive(), response.getIsActive());
//    }
//}