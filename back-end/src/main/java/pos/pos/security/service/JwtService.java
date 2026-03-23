package pos.pos.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {

    private static final String ROLES_CLAIM = "roles";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";

    @Value("${security.jwt.secret}")
    private String secret;

    @Getter
    @Value("${security.jwt.access-expiration}")
    private Duration accessTokenExpiration;

    @Value("${security.jwt.refresh-expiration}")
    private Duration refreshTokenExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UUID userId, List<String> roles, UUID tokenId) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim(ROLES_CLAIM, roles)
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration.toMillis()))
                .signWith(key)
                .compact();
    }

    public String generateAccessToken(UUID userId) {
        return generateAccessToken(userId, List.of(), UUID.randomUUID());
    }

    public String generateRefreshToken(UUID userId, UUID tokenId) {
        return Jwts.builder()
                .subject(userId.toString())
                .id(tokenId.toString())
                .claim(TOKEN_TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration.toMillis()))
                .signWith(key)
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

    public boolean isAccessToken(String token) {
        try {
            return ACCESS_TOKEN_TYPE.equals(parse(token).get(TOKEN_TYPE_CLAIM, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
