package com.soprahr.interne.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.repository.UserRepository;

class BootstrapAdminInitializerTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository repository;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
    }

    private BootstrapAdminInitializer init(String email, String password) {
        return new BootstrapAdminInitializer(repository, encoder, email, password);
    }

    @Test
    void createsAnEnabledAdminWithAHashedPassword() {
        init("  Admin@Test.LOCAL ", "Str0ng-Passw0rd").run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repository).save(saved.capture());
        User admin = saved.getValue();
        assertEquals("admin@test.local", admin.getEmail());
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.isEnabled());
        assertTrue(encoder.matches("Str0ng-Passw0rd", admin.getPasswordHash()));
    }

    @Test
    void neverTouchesAnExistingAccount() {
        when(repository.existsByEmailIgnoreCase("admin@test.local")).thenReturn(true);
        init("admin@test.local", "Str0ng-Passw0rd").run(null);
        verify(repository, never()).save(any());
    }

    @Test
    void doesNothingWithoutVariablesOrWithInvalidOnes() {
        init("", "").run(null);
        init("admin@test.local", "").run(null);
        init("", "Str0ng-Passw0rd").run(null);
        init("admin@test.local", "short").run(null);
        init("admin@test.local", "x".repeat(73)).run(null);
        verify(repository, never()).save(any());
    }
}
