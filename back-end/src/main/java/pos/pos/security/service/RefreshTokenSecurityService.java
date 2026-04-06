package pos.pos.security.service;

import jakarta.annotation.PostConstruct;
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

    @PostConstruct
    void validateConfiguration() {
        if (refreshTokenPepper == null || refreshTokenPepper.isBlank()) {
            throw new IllegalStateException("app.security.refresh-token.pepper must not be blank");
        }
        if (refreshTokenPepper.length() < 32) {
            throw new IllegalStateException("app.security.refresh-token.pepper must be at least 32 characters");
        }
    }

    public ValidatedRefreshToken validate(String refreshToken) {
        String normalizedRefreshToken = normalize(refreshToken);

        if (!jwtService.isRefreshToken(normalizedRefreshToken)) {
            throw invalidRefreshToken();
        }

        UUID tokenId;
        UUID userId;

        try {
            tokenId = jwtService.extractTokenId(normalizedRefreshToken);
            userId = jwtService.extractUserId(normalizedRefreshToken);
        } catch (RuntimeException ex) {
            throw invalidRefreshToken();
        }

        return new ValidatedRefreshToken(
                normalizedRefreshToken,
                hashNormalized(normalizedRefreshToken),
                tokenId,
                userId
        );
    }

    public String hash(String refreshToken) {
        return hashNormalized(normalize(refreshToken));
    }

    public boolean matchesHash(ValidatedRefreshToken refreshToken, String storedHash) {
        if (storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.UTF_8),
                refreshToken.tokenHash().getBytes(StandardCharsets.UTF_8)
        );
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
