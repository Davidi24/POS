package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.security.dto.JwksResponse;
import pos.pos.security.service.JwtService;

@Tag(name = "Authentication / JWKS")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class JwksController {

    private final JwtService jwtService;

    @GetMapping("/.well-known/jwks.json")
    public JwksResponse getJwks() {
        return jwtService.getJwks();
    }
}
