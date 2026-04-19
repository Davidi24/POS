package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.dto.CurrentUserResponse;
import pos.pos.auth.service.AuthProfileService;


@Tag(name = "Authentication / Profile")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthProfileController {

    private final AuthProfileService authProfileService;

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authProfileService.getMe(authentication));
    }
}
