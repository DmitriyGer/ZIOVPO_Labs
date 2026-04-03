package ru.mfa.airline.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import ru.mfa.airline.model.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final SecretKey accessKey;
    private final SecretKey refreshKey;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public JwtTokenProvider(@Value("${jwt.access.secret}") String accessSecret,
            @Value("${jwt.refresh.secret}") String refreshSecret,
            @Value("${jwt.access.ttl-minutes}") long accessTtlMinutes,
            @Value("${jwt.refresh.ttl-hours}") long refreshTtlHours) {
        this.accessKey = buildKey(accessSecret);
        this.refreshKey = buildKey(refreshSecret);
        this.accessTtl = Duration.ofMinutes(accessTtlMinutes);
        this.refreshTtl = Duration.ofHours(refreshTtlHours);
    }

    public String generateAccessToken(User user, Long sessionId) {
        return buildToken(user, sessionId, TokenType.ACCESS, accessKey, accessTtl);
    }

    public String generateRefreshToken(User user, Long sessionId) {
        return buildToken(user, sessionId, TokenType.REFRESH, refreshKey, refreshTtl);
    }

    private String buildToken(User user, Long sessionId, TokenType type, SecretKey key, Duration ttl) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiry = now.plus(ttl);

        return Jwts.builder()
                .setSubject(user.getUsername())
                .setIssuedAt(Date.from(now.toInstant()))
                .setExpiration(Date.from(expiry.toInstant()))
                .addClaims(Map.of(
                        "userId", user.getId(),
                        "role", user.getRole(),
                        "sessionId", sessionId,
                        "tokenType", type.value))
                .signWith(key)
                .compact();
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, TokenType.ACCESS);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, TokenType.REFRESH);
    }

    public String getUsername(String token, TokenType type) {
        Claims claims = parseClaims(token, type == TokenType.ACCESS ? accessKey : refreshKey);
        return claims.getSubject();
    }

    public Long getSessionId(String token) {
        Claims claims = parseClaims(token, accessKey);
        return claims.get("sessionId", Number.class).longValue();
    }

    public Duration getAccessTtl() {
        return accessTtl;
    }

    public Duration getRefreshTtl() {
        return refreshTtl;
    }

    private boolean validateToken(String token, TokenType expectedType) {
        try {
            Claims claims = parseClaims(token, expectedType == TokenType.ACCESS ? accessKey : refreshKey);
            String tokenType = claims.get("tokenType", String.class);
            if (!expectedType.value.equals(tokenType)) {
                throw new BadCredentialsException("Token type mismatch");
            }
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims parseClaims(String token, SecretKey key) {
        Jws<Claims> claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);
        return claims.getBody();
    }

    private SecretKey buildKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public enum TokenType {
        ACCESS("access"),
        REFRESH("refresh");

        private final String value;

        TokenType(String value) {
            this.value = value;
        }
    }
}
