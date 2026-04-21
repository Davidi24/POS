package pos.pos.security.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pos.pos.exception.auth.InvalidTokenException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// checked
// tested
@Service
public class OpaqueTokenService {

    private static final int TOKEN_SIZE_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generate() {
        byte[] tokenBytes = new byte[TOKEN_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    public IssuedToken issue(String pepper) {
        String rawToken = generate();
        return new IssuedToken(rawToken, hash(rawToken, pepper));
    }

    public String normalize(String token) {
        if (!StringUtils.hasText(token)) {
            throw new InvalidTokenException();
        }

        return token.trim();
    }

    public String hash(String rawToken, String pepper) {
        String normalizedToken = normalize(rawToken);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((normalizedToken + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash opaque token", ex);
        }
    }

    public record IssuedToken(String rawToken, String tokenHash) {
    }
}
