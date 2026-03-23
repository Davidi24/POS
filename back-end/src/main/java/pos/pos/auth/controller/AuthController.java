package pos.pos.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pos.pos.auth.dto.AuthTokensResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.dto.LoginResponse;
import pos.pos.auth.service.AuthService;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.security.util.CookieService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientInfoExtractor clientInfoExtractor;
    private final CookieService cookieService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        ClientInfo clientInfo = clientInfoExtractor.extract(httpRequest);

        AuthTokensResponse authResult = authService.login(request, clientInfo);

        cookieService.addRefreshTokenCookie(httpResponse, authResult.getRefreshToken());

        LoginResponse response = LoginResponse.builder()
                .accessToken(authResult.getAccessToken())
                .tokenType(authResult.getTokenType())
                .expiresIn(authResult.getExpiresIn())
                .user(authResult.getUser())
                .build();

        return ResponseEntity.ok(response);
    }
}
