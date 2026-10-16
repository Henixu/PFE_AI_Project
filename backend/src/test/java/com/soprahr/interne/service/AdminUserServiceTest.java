package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.soprahr.interne.dto.CreateUserRequest;
import com.soprahr.interne.dto.UserSummaryResponse;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.exception.BusinessRuleException;
import com.soprahr.interne.exception.ConflictException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.EmployeeRepository;
import com.soprahr.interne.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminUserServiceTest {

    private static final String ADMIN_EMAIL = "admin@test.local";

    @Mock UserRepository userRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock AuditService auditService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AdminUserService service;

    private User admin;
    private User alice;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(userRepository, employeeRepository, encoder, auditService);
        admin = user(1L, ADMIN_EMAIL, Role.ADMIN, true);
        alice = user(2L, "alice@test.local", Role.EMPLOYEE, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findById(2L)).thenReturn(Optional.of(alice));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            if (u.getId() == null) {
                ReflectionTestUtils.setField(u, "id", 99L);
            }
            return u;
        });
        when(employeeRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
    }

    // ---- création ----

    @Test
    void createHashesPasswordNormalizesEmailAndAudits() {
        UserSummaryResponse r = service.create(
                new CreateUserRequest("  New@Test.LOCAL ", "Secret123!", Role.HR, null, null), ADMIN_EMAIL);

        assertEquals("new@test.local", r.email());
        assertEquals(Role.HR, r.role());
        assertTrue(r.enabled(), "actif par défaut");
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                u -> encoder.matches("Secret123!", u.getPasswordHash()) && !"Secret123!".equals(u.getPasswordHash())));
        verify(auditService).record(eq(ADMIN_EMAIL), eq("USER_CREATE"), eq("User"), eq(99L), anyString());
    }

    @Test
    void createRejectsDuplicateEmailIgnoringCase() {
        when(userRepository.existsByEmailIgnoreCase("alice@test.local")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.create(
                new CreateUserRequest("ALICE@test.local", "Secret123!", Role.EMPLOYEE, null, null), ADMIN_EMAIL));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createWithEmployeeLinksThemAndRejectsAlreadyLinkedEmployee() {
        Employee free = employee(10L, null);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(free));
        UserSummaryResponse r = service.create(
                new CreateUserRequest("e@test.local", "Secret123!", Role.EMPLOYEE, true, 10L), ADMIN_EMAIL);
        assertEquals(10L, r.employeeId());
        assertEquals(99L, free.getUser().getId());

        Employee taken = employee(11L, alice);
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(taken));
        assertThrows(ConflictException.class, () -> service.create(
                new CreateUserRequest("f@test.local", "Secret123!", Role.EMPLOYEE, true, 11L), ADMIN_EMAIL));
    }

    // ---- rôles ----

    @Test
    void changeRoleUpdatesAndAudits() {
        UserSummaryResponse r = service.changeRole(2L, Role.MANAGER, ADMIN_EMAIL);
        assertEquals(Role.MANAGER, r.role());
        verify(auditService).record(eq(ADMIN_EMAIL), eq("USER_ROLE_CHANGE"), eq("User"), eq(2L),
                org.mockito.ArgumentMatchers.contains("EMPLOYEE -> MANAGER"));
    }

    @Test
    void cannotChangeOwnRole() {
        assertThrows(BusinessRuleException.class, () -> service.changeRole(1L, Role.EMPLOYEE, ADMIN_EMAIL));
        assertEquals(Role.ADMIN, admin.getRole());
    }

    @Test
    void cannotDemoteLastActiveAdmin() {
        User other = user(3L, "other@test.local", Role.ADMIN, true);
        when(userRepository.findById(3L)).thenReturn(Optional.of(other));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> service.changeRole(3L, Role.HR, ADMIN_EMAIL));
        assertEquals(Role.ADMIN, other.getRole());

        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(2L);
        service.changeRole(3L, Role.HR, ADMIN_EMAIL);
        assertEquals(Role.HR, other.getRole());
    }

    // ---- activation ----

    @Test
    void disableBlocksLoginFlagAndIsAudited() {
        UserSummaryResponse r = service.setEnabled(2L, false, ADMIN_EMAIL);
        assertFalse(r.enabled());
        assertFalse(alice.isEnabled());
        verify(auditService).record(eq(ADMIN_EMAIL), eq("USER_DISABLE"), eq("User"), eq(2L), anyString());

        service.setEnabled(2L, true, ADMIN_EMAIL);
        assertTrue(alice.isEnabled());
        verify(auditService).record(eq(ADMIN_EMAIL), eq("USER_ENABLE"), eq("User"), eq(2L), anyString());
    }

    @Test
    void cannotDisableSelfOrLastActiveAdmin() {
        assertThrows(BusinessRuleException.class, () -> service.setEnabled(1L, false, ADMIN_EMAIL));
        assertTrue(admin.isEnabled());

        User other = user(3L, "other@test.local", Role.ADMIN, true);
        when(userRepository.findById(3L)).thenReturn(Optional.of(other));
        when(userRepository.countByRoleAndEnabledTrue(Role.ADMIN)).thenReturn(1L);
        assertThrows(BusinessRuleException.class, () -> service.setEnabled(3L, false, ADMIN_EMAIL));
    }

    // ---- e-mail ----

    @Test
    void updateEmailRejectsDuplicateButAllowsOwnEmailCaseChange() {
        when(userRepository.existsByEmailIgnoreCase("taken@test.local")).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.updateEmail(2L, "taken@test.local", ADMIN_EMAIL));

        when(userRepository.existsByEmailIgnoreCase("alice@test.local")).thenReturn(true);
        UserSummaryResponse r = service.updateEmail(2L, "ALICE@test.local", ADMIN_EMAIL);
        assertEquals("alice@test.local", r.email());
    }

    // ---- association employé ----

    @Test
    void linkEmployeeRejectsDuplicatesInBothDirections() {
        Employee free = employee(10L, null);
        Employee takenByOther = employee(11L, admin);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(free));
        when(employeeRepository.findById(11L)).thenReturn(Optional.of(takenByOther));

        // employé déjà pris par un autre compte
        assertThrows(ConflictException.class, () -> service.linkEmployee(2L, 11L, ADMIN_EMAIL));

        // compte déjà associé à un autre employé
        Employee aliceEmp = employee(12L, alice);
        when(employeeRepository.findById(12L)).thenReturn(Optional.of(aliceEmp));
        when(employeeRepository.findByUserId(2L)).thenReturn(Optional.of(aliceEmp));
        assertThrows(ConflictException.class, () -> service.linkEmployee(2L, 10L, ADMIN_EMAIL));
        assertNull(free.getUser());

        // ré-association de la même paire : idempotente
        UserSummaryResponse same = service.linkEmployee(2L, 12L, ADMIN_EMAIL);
        assertEquals(12L, same.employeeId());
    }

    @Test
    void linkAndUnlinkEmployee() {
        Employee free = employee(10L, null);
        when(employeeRepository.findById(10L)).thenReturn(Optional.of(free));
        UserSummaryResponse linked = service.linkEmployee(2L, 10L, ADMIN_EMAIL);
        assertEquals(10L, linked.employeeId());
        verify(auditService).record(eq(ADMIN_EMAIL), eq("USER_LINK_EMPLOYEE"), eq("User"), eq(2L), anyString());

        when(employeeRepository.findByUserId(2L)).thenReturn(Optional.of(free));
        UserSummaryResponse unlinked = service.unlinkEmployee(2L, ADMIN_EMAIL);
        assertNull(unlinked.employeeId());
        assertNull(free.getUser());

        when(employeeRepository.findByUserId(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.unlinkEmployee(2L, ADMIN_EMAIL));
    }

    @Test
    void unknownUserIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.get(404L));
        assertNotEquals(0, service.get(2L).id());
    }

    // ---- fabriques ----

    private static User user(Long id, String email, Role role, boolean enabled) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setEmail(email);
        u.setPasswordHash("hash");
        u.setRole(role);
        u.setEnabled(enabled);
        return u;
    }

    private static Employee employee(Long id, User user) {
        Employee e = new Employee();
        ReflectionTestUtils.setField(e, "id", id);
        e.setEmployeeNumber("EMP-" + id);
        e.setFirstName("First" + id);
        e.setLastName("Last" + id);
        e.setUser(user);
        return e;
    }
}
