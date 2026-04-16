package pos.pos.auth.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pos.pos.exception.auth.InvalidTokenException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;


// checked
// tested
@Service
public class OneTimeCodeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public IssuedCode issueNumericCode(int length, String pepper) {
        String rawCode = generateNumericCode(length);
        return new IssuedCode(rawCode, hash(rawCode, pepper));
    }

    public String hash(String rawCode, String pepper) {
        String normalizedCode = normalize(rawCode);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((normalizedCode + pepper).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to hash one-time code", ex);
        }
    }

    public String normalize(String rawCode) {
        if (!StringUtils.hasText(rawCode)) {
            throw new InvalidTokenException();
        }

        return rawCode.trim();
    }

    private String generateNumericCode(int length) {
        if (length < 4 || length > 8) {
            throw new IllegalArgumentException("Code length must be between 4 and 8 digits");
        }

        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(SECURE_RANDOM.nextInt(10));
        }
        return code.toString();
    }

    public record IssuedCode(String rawCode, String codeHash) {
    }
}
