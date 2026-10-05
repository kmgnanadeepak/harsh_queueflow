package com.harsha.queueflow.security;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import com.harsha.queueflow.config.QueueFlowProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies QueueFlow's own JWT access tokens.
 *
 * <p>The token payload is intentionally minimal: subject (user id), email, role,
 * provider, issued-at and expiry. Passwords, Google access tokens and any other
 * sensitive data are never embedded.</p>
 */
@Component
public class JwtUtil {

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_PROVIDER = "provider";

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final Key signingKey;
    private final String issuer;
    private final long expirationMillis;

    public JwtUtil(QueueFlowProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "queueflow.jwt.secret is not configured. Set the JWT_SECRET environment variable "
                            + "(see .env.example) or activate the 'dev' profile for local development.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "queueflow.jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes long for HS256.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.issuer = properties.getJwt().getIssuer();
        this.expirationMillis = properties.getJwt().getExpiration().toMillis();
    }

    public String generateToken(QueueFlowPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMillis);
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(principal.getId()))
                .claim(CLAIM_EMAIL, principal.getEmail())
                .claim(CLAIM_NAME, principal.getName())
                .claim(CLAIM_ROLE, principal.getRole().name())
                .claim(CLAIM_PROVIDER, principal.getProvider())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith((SecretKey) signingKey)
                .compact();
    }

    /** @return the parsed claims, or {@code null} when the token is invalid or expired. */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith((SecretKey) signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            log.debug("Rejected expired JWT: {}", ex.getMessage());
            return null;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Rejected invalid JWT: {}", ex.getMessage());
            return null;
        }
    }

    public Long extractUserId(Claims claims) {
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}
