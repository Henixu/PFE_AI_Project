package com.soprahr.interne.security;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.soprahr.interne.entity.enums.Role;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Génération et vérification des JWT (HS256). La signature, l'émetteur et l'expiration
 * sont contrôlés à chaque vérification ; le secret vient exclusivement de la configuration.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs,
            @Value("${app.jwt.issuer:soprahr-interne}") String issuer) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET est obligatoire (secret Base64 d'au moins 32 octets)");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET doit être encodé en Base64", e);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET trop court : 32 octets minimum après décodage Base64");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
        this.issuer = issuer;
    }

    public IssuedToken generateToken(String email, Role role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expirationMs);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(email)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** Retourne l'e-mail du jeton s'il est valide (signature, émetteur, non expiré), sinon vide. */
    public Optional<String> extractEmail(String token) {
        try {
            return Optional.ofNullable(Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
