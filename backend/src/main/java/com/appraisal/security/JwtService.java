package com.appraisal.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.ttl-hours:12}") long ttlHours) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("app.jwt.secret must be at least 32 characters");
        this.key = Keys.hmacShaKeyFor(bytes);
        this.ttl = Duration.ofHours(ttlHours);
    }

    public String issue(long userId, String username) {
        Date now = new Date();
        return Jwts.builder().subject(String.valueOf(userId)).claim("username", username)
                .issuedAt(now).expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key).compact();
    }

    /** Returns the user id, or null when the token is invalid / expired. */
    public Long parse(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Long.valueOf(c.getSubject());
        } catch (Exception e) {
            return null;
        }
    }
}
