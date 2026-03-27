package pos.pos.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pos.pos.exception.auth.InvalidCredentialsException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenSecurityService {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    private final JwtService jwtService;

    @Value("${app.security.refresh-token.pepper}")
    private String refreshTokenPepper;

    public ValidatedRefreshToken validate(String refreshToken) {
        String normalizedRefreshToken = normalize(refreshToken);

        if (!jwtService.isValid(normalizedRefreshToken) || !jwtService.isRefreshToken(normalizedRefreshToken)) {
            throw invalidRefreshToken();
        }

        return new ValidatedRefreshToken(
                normalizedRefreshToken,
                hashNormalized(normalizedRefreshToken),
                jwtService.extractTokenId(normalizedRefreshToken),
                jwtService.extractUserId(normalizedRefreshToken)
        );
    }

    public String hash(String refreshToken) {
        return hashNormalized(normalize(refreshToken));
    }

    public boolean matchesHash(ValidatedRefreshToken refreshToken, String storedHash) {
        return storedHash != null && storedHash.equals(refreshToken.tokenHash());
    }

    private String normalize(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }

        return refreshToken.trim();
    }

    private String hashNormalized(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((refreshToken + refreshTokenPepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash refresh token", ex);
        }
    }

    private InvalidCredentialsException invalidRefreshToken() {
        return new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
    }

    public record ValidatedRefreshToken(
            String token,
            String tokenHash,
            UUID tokenId,
            UUID userId
    ) {
    }
}
