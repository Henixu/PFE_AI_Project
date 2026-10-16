package com.soprahr.interne.config;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.repository.UserRepository;

/**
 * Crée le premier administrateur au démarrage lorsque BOOTSTRAP_ADMIN_EMAIL et BOOTSTRAP_ADMIN_PASSWORD
 * sont définis (backend/.env, non versionné). Ne modifie jamais un compte existant, n'écrit jamais
 * le mot de passe dans les logs, et ne fait rien si les variables sont absentes.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;

    public BootstrapAdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.email = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isEmpty() && password.isEmpty()) {
            return;
        }
        if (email.isEmpty() || password.isEmpty()) {
            log.warn("Administrateur initial ignoré : BOOTSTRAP_ADMIN_EMAIL et BOOTSTRAP_ADMIN_PASSWORD doivent être définis ensemble");
            return;
        }
        if (password.length() < 8 || password.length() > 72) {
            log.warn("Administrateur initial ignoré : le mot de passe doit contenir entre 8 et 72 caractères");
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            log.info("Administrateur initial : le compte {} existe déjà, rien à faire", email);
            return;
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);
        log.info("Administrateur initial créé : {} (pensez à changer son mot de passe puis à retirer BOOTSTRAP_ADMIN_PASSWORD du .env)", email);
    }
}
