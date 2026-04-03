package pos.pos.auth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.service.AuthRegisterService;
import pos.pos.user.dto.CreateUserRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class UserManagementController {

    private final AuthRegisterService authRegisterService;

    @PostMapping("/register")
    @PreAuthorize("hasAuthority('USERS_CREATE')")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication
    ) {
        User caller = (User) authentication.getPrincipal();
        UserResponse response = authRegisterService.register(request, caller.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

}