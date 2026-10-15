package com.soprahr.interne.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.soprahr.interne.entity.enums.Role;

class JwtServiceTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]).replace('A', 'B');
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(new byte[32]).replace('A', 'C');

    private JwtService service(String secret, long ttlMs) {
        return new JwtService(secret, ttlMs, "test");
    }

    @Test
    void validTokenReturnsEmail() {
        JwtService jwt = service(SECRET, 60_000);
        String token = jwt.generateToken("a@b.test", Role.HR).token();
        assertEquals("a@b.test", jwt.extractEmail(token).orElseThrow());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService jwt = service(SECRET, -1_000);
        String token = jwt.generateToken("a@b.test", Role.HR).token();
        assertTrue(jwt.extractEmail(token).isEmpty());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String token = service(OTHER_SECRET, 60_000).generateToken("a@b.test", Role.ADMIN).token();
        assertTrue(service(SECRET, 60_000).extractEmail(token).isEmpty());
    }

    @Test
    void garbageAndTamperedTokensAreRejected() {
        JwtService jwt = service(SECRET, 60_000);
        String token = jwt.generateToken("a@b.test", Role.HR).token();
        assertTrue(jwt.extractEmail("not-a-jwt").isEmpty());
        assertTrue(jwt.extractEmail(token + "x").isEmpty());
    }

    @Test
    void missingOrShortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> service("", 1000));
        assertThrows(IllegalStateException.class, () -> service(Base64.getEncoder().encodeToString(new byte[8]), 1000));
    }
}
