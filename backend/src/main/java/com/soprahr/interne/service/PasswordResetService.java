package com.soprahr.interne.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.entity.PasswordResetToken;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.exception.InvalidResetTokenException;
import com.soprahr.interne.repository.PasswordResetTokenRepository;
import com.soprahr.interne.repository.UserRepository;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetNotifier notifier;
    private final Duration tokenTtl;
    private final String frontendUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository, PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder, PasswordResetNotifier notifier,
            @Value("${app.password-reset.expiration-minutes:30}") long ttlMinutes,
            @Value("${app.password-reset.frontend-url}") String frontendUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.notifier = notifier;
        this.tokenTtl = Duration.ofMinutes(ttlMinutes);
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
    }

    /** Ne révèle jamais si l'e-mail correspond à un compte : le contrôleur répond toujours pareil. */
    @Transactional
    public void requestReset(String email) {
        userRepository.findByEmailIgnoreCase(email.trim())
                .filter(User::isEnabled)
                .ifPresent(user -> {
                    tokenRepository.deleteAllByUserId(user.getId());
                    String rawToken = newRawToken();
                    PasswordResetToken token = new PasswordResetToken();
                    token.setUser(user);
                    token.setTokenHash(sha256(rawToken));
                    token.setExpiresAt(Instant.now().plus(tokenTtl));
                    tokenRepository.save(token);
                    notifier.sendResetLink(user.getEmail(), frontendUrl + "/reset-password?token=" + rawToken);
                });
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(InvalidResetTokenException::new);
        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(Instant.now());
        // invalide tout autre lien encore en circulation pour ce compte
        tokenRepository.flush();
        tokenRepository.deleteAllByUserId(user.getId());
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
