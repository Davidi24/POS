package pos.pos.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.dto.RegisterRequest;
import pos.pos.auth.mapper.AuthMapper;
import pos.pos.exception.auth.EmailAlreadyExistsException;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegisterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private AuthMapper authMapper;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest request;
    private User user;
    private UserResponse response;

    @BeforeEach
    void setup() {
        request = new RegisterRequest();
        request.setEmail("test@test.com");
        request.setPassword("Password123");
        request.setFirstName("John");
        request.setLastName("Doe");

        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());

        response = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

    @Test
    void register_success_whenEmailNotExists() {

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordService.hash(request.getPassword())).thenReturn("hashedPassword");
        when(authMapper.toUser(request, "hashedPassword")).thenReturn(user);
        when(userRepository.save(user)).thenReturn(user);
        when(authMapper.toUserResponse(user)).thenReturn(response);

        UserResponse result = authService.register(request);

        assertNotNull(result);
        assertEquals(request.getEmail(), result.getEmail());

        verify(userRepository).existsByEmail(request.getEmail());
        verify(passwordService).hash(request.getPassword());
        verify(authMapper).toUser(request, "hashedPassword");
        verify(userRepository).save(user);
        verify(authMapper).toUserResponse(user);
    }

    @Test
    void register_fail_whenEmailAlreadyExists() {

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.register(request)
        );

        verify(userRepository).existsByEmail(request.getEmail());
        verify(passwordService, never()).hash(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_shouldHashPassword() {

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordService.hash(request.getPassword())).thenReturn("hashedPassword");
        when(authMapper.toUser(request, "hashedPassword")).thenReturn(user);
        when(authMapper.toUserResponse(user)).thenReturn(response);

        authService.register(request);

        verify(passwordService).hash(request.getPassword());
    }

    @Test
    void register_shouldSaveUser() {

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordService.hash(request.getPassword())).thenReturn("hashedPassword");
        when(authMapper.toUser(request, "hashedPassword")).thenReturn(user);
        when(authMapper.toUserResponse(user)).thenReturn(response);

        authService.register(request);

        verify(userRepository).save(user);
    }

    @Test
    void register_shouldMapRequestToUser() {

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordService.hash(request.getPassword())).thenReturn("hashedPassword");
        when(authMapper.toUser(request, "hashedPassword")).thenReturn(user);
        when(authMapper.toUserResponse(user)).thenReturn(response);

        authService.register(request);

        verify(authMapper).toUser(request, "hashedPassword");
    }

    @Test
    void register_shouldReturnUserResponse() {

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordService.hash(request.getPassword())).thenReturn("hashedPassword");
        when(authMapper.toUser(request, "hashedPassword")).thenReturn(user);
        when(authMapper.toUserResponse(user)).thenReturn(response);

        UserResponse result = authService.register(request);

        assertNotNull(result);
        assertEquals(user.getEmail(), result.getEmail());

        verify(authMapper).toUserResponse(user);
    }
}