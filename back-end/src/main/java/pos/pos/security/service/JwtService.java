package pos.pos.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pos.pos.security.dto.JwksResponse;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

// tested
// checked
@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String JWKS_KEY_TYPE = "RSA";
    private static final String JWKS_KEY_USE = "sig";
    private static final String JWT_ALGORITHM = "RS256";
    private static final int MINIMUM_RSA_KEY_SIZE_BITS = 2048;

    @Value("${security.jwt.private-key}")
    private String privateKeyPem;

    @Value("${security.jwt.public-key}")
    private String publicKeyPem;

    @Getter
    @Value("${security.jwt.access-expiration}")
    private Duration accessTokenExpiration;

    @Value("${security.jwt.refresh-expiration}")
    private Duration refreshTokenExpiration;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;
    private String keyId;
    @Getter
    private JwksResponse jwks;

    @PostConstruct
    public void init() {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalStateException("security.jwt.private-key must not be blank");
        }
        if (publicKeyPem == null || publicKeyPem.isBlank()) {
            throw new IllegalStateException("security.jwt.public-key must not be blank");
        }
        if (accessTokenExpiration == null || accessTokenExpiration.isZero() || accessTokenExpiration.isNegative()) {
            throw new IllegalStateException("security.jwt.access-expiration must be positive");
        }
        if (refreshTokenExpiration == null || refreshTokenExpiration.isZero() || refreshTokenExpiration.isNegative()) {
            throw new IllegalStateException("security.jwt.refresh-expiration must be positive");
        }

        try {
            privateKey = parsePrivateKey(privateKeyPem);
            publicKey = parsePublicKey(publicKeyPem);
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("security.jwt keys must be valid RSA PEM values", ex);
        }

        if (privateKey.getModulus().bitLength() < MINIMUM_RSA_KEY_SIZE_BITS
                || publicKey.getModulus().bitLength() < MINIMUM_RSA_KEY_SIZE_BITS) {
            throw new IllegalStateException("security.jwt RSA keys must be at least 2048 bits");
        }

        if (!privateKey.getModulus().equals(publicKey.getModulus())) {
            throw new IllegalStateException("security.jwt.public-key must match security.jwt.private-key");
        }

        keyId = encodeBase64Url(sha256(publicKey.getEncoded()));
        jwks = new JwksResponse(List.of(new JwksResponse.JwkKeyResponse(
                JWKS_KEY_TYPE,
                JWKS_KEY_USE,
                JWT_ALGORITHM,
                keyId,
                encodeBase64Url(unsignedBytes(publicKey.getModulus().toByteArray())),
                encodeBase64Url(unsignedBytes(publicKey.getPublicExponent().toByteArray()))
        )));
    }

    public String generateAccessToken(UUID userId, List<String> roles, UUID tokenId) {
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim(ROLES_CLAIM, roles)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration.toMillis()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String generateRefreshToken(UUID userId, UUID tokenId) {
        return Jwts.builder()
                .header()
                .keyId(keyId)
                .and()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration.toMillis()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public UUID extractUserId(String token) {
        Claims claims = parse(token);
        return UUID.fromString(claims.getSubject());
    }

    public UUID extractTokenId(String token) {
        Claims claims = parse(token);
        return UUID.fromString(claims.getId());
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(String token) {
        try {
            return ACCESS_TOKEN_TYPE.equals(parse(token).get(TOKEN_TYPE_CLAIM, String.class));
        } catch (Exception e) {
            return false;
        }
    }


    public boolean isRefreshToken(String token) {
        try {
            return REFRESH_TOKEN_TYPE.equals(parse(token).get(TOKEN_TYPE_CLAIM, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private RSAPrivateKey parsePrivateKey(String pem) throws Exception {
        String normalizedPem = normalizePem(pem);
        if (normalizedPem.contains("BEGIN RSA PRIVATE KEY")) {
            throw new IllegalStateException("security.jwt.private-key must use PKCS#8 PEM format");
        }

        byte[] keyBytes = Base64.getDecoder().decode(stripPemMarkers(normalizedPem, "PRIVATE KEY"));
        return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private RSAPublicKey parsePublicKey(String pem) throws Exception {
        String normalizedPem = normalizePem(pem);
        if (normalizedPem.contains("BEGIN RSA PUBLIC KEY")) {
            throw new IllegalStateException("security.jwt.public-key must use X.509 PEM format");
        }

        byte[] keyBytes = Base64.getDecoder().decode(stripPemMarkers(normalizedPem, "PUBLIC KEY"));
        PublicKey parsedKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        return (RSAPublicKey) parsedKey;
    }

    private String normalizePem(String pem) {
        return pem.replace("\\n", "\n").trim();
    }

    private String stripPemMarkers(String pem, String type) {
        return pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
    }

    private byte[] unsignedBytes(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private String encodeBase64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute JWT key id", ex);
        }
    }
}
