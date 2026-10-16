package com.soprahr.interne.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.dto.CreateUserRequest;
import com.soprahr.interne.dto.EmployeeOptionResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.dto.UserSummaryResponse;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.exception.BusinessRuleException;
import com.soprahr.interne.exception.ConflictException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.EmployeeRepository;
import com.soprahr.interne.repository.UserRepository;

/**
 * Gestion des comptes. Réservée au rôle ADMIN (en plus de la règle d'URL /api/admin/**,
 * pour qu'un futur appel interne ne contourne pas la sécurité). Chaque opération sensible est auditée.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class AdminUserService {

    static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("email", "role", "enabled", "createdAt", "lastLoginAt");

    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository, EmployeeRepository employeeRepository,
            PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummaryResponse> list(String search, Role role, Boolean enabled, int page, int size,
            String sortBy, String direction) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC,
                        SORTABLE.contains(sortBy) ? sortBy : "createdAt"));

        Page<User> users = userRepository.findAll(filter(search, role, enabled), pageable);

        Map<Long, Employee> employeesByUser = employeeRepository
                .findByUserIdIn(users.getContent().stream().map(User::getId).toList()).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), Function.identity()));
        return PageResponse.of(users, users.getContent().stream()
                .map(u -> toSummary(u, employeesByUser.get(u.getId()))).toList());
    }

    @Transactional(readOnly = true)
    public UserSummaryResponse get(Long id) {
        User user = find(id);
        return toSummary(user, employeeRepository.findByUserId(id).orElse(null));
    }

    public UserSummaryResponse create(CreateUserRequest request, String actorEmail) {
        String email = normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Un compte existe déjà avec cet e-mail");
        }
        Employee employee = null;
        if (request.employeeId() != null) {
            employee = findLinkableEmployee(request.employeeId());
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setEnabled(request.enabled() == null || request.enabled());
        user = userRepository.save(user);

        if (employee != null) {
            employee.setUser(user);
        }
        auditService.record(actorEmail, "USER_CREATE", "User", user.getId(),
                "email=" + email + ", role=" + user.getRole() + ", enabled=" + user.isEnabled()
                        + (employee != null ? ", employeeId=" + employee.getId() : ""));
        return toSummary(user, employee);
    }

    public UserSummaryResponse updateEmail(Long id, String newEmail, String actorEmail) {
        User user = find(id);
        String email = normalize(newEmail);
        if (!email.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Un compte existe déjà avec cet e-mail");
        }
        String old = user.getEmail();
        user.setEmail(email);
        auditService.record(actorEmail, "USER_UPDATE", "User", id, "email: " + old + " -> " + email);
        return toSummary(user, employeeRepository.findByUserId(id).orElse(null));
    }

    public UserSummaryResponse changeRole(Long id, Role newRole, String actorEmail) {
        User user = find(id);
        if (user.getRole() == newRole) {
            return toSummary(user, employeeRepository.findByUserId(id).orElse(null));
        }
        if (isSelf(user, actorEmail)) {
            throw new BusinessRuleException("Vous ne pouvez pas modifier votre propre rôle");
        }
        if (user.getRole() == Role.ADMIN) {
            ensureAnotherActiveAdmin(user);
        }
        Role old = user.getRole();
        user.setRole(newRole);
        auditService.record(actorEmail, "USER_ROLE_CHANGE", "User", id, "role: " + old + " -> " + newRole);
        return toSummary(user, employeeRepository.findByUserId(id).orElse(null));
    }

    public UserSummaryResponse setEnabled(Long id, boolean enabled, String actorEmail) {
        User user = find(id);
        if (user.isEnabled() == enabled) {
            return toSummary(user, employeeRepository.findByUserId(id).orElse(null));
        }
        if (!enabled) {
            if (isSelf(user, actorEmail)) {
                throw new BusinessRuleException("Vous ne pouvez pas désactiver votre propre compte");
            }
            if (user.getRole() == Role.ADMIN) {
                ensureAnotherActiveAdmin(user);
            }
        }
        user.setEnabled(enabled);
        auditService.record(actorEmail, enabled ? "USER_ENABLE" : "USER_DISABLE", "User", id,
                "enabled=" + enabled);
        return toSummary(user, employeeRepository.findByUserId(id).orElse(null));
    }

    public UserSummaryResponse linkEmployee(Long userId, Long employeeId, String actorEmail) {
        User user = find(userId);
        if (employeeRepository.findByUserId(userId).filter(e -> !e.getId().equals(employeeId)).isPresent()) {
            throw new ConflictException("Ce compte est déjà associé à un autre employé");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable"));
        if (employee.getUser() != null && !employee.getUser().getId().equals(userId)) {
            throw new ConflictException("Cet employé est déjà associé à un autre compte");
        }
        employee.setUser(user);
        auditService.record(actorEmail, "USER_LINK_EMPLOYEE", "User", userId, "employeeId=" + employeeId);
        return toSummary(user, employee);
    }

    public UserSummaryResponse unlinkEmployee(Long userId, String actorEmail) {
        User user = find(userId);
        Employee employee = employeeRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Ce compte n'est associé à aucun employé"));
        employee.setUser(null);
        auditService.record(actorEmail, "USER_UNLINK_EMPLOYEE", "User", userId,
                "employeeId=" + employee.getId());
        return toSummary(user, null);
    }

    @Transactional(readOnly = true)
    public List<EmployeeOptionResponse> linkableEmployees() {
        return employeeRepository.findByUserIsNullOrderByLastNameAscFirstNameAsc().stream()
                .map(e -> new EmployeeOptionResponse(e.getId(), e.getEmployeeNumber(),
                        e.getFirstName() + " " + e.getLastName(), e.getJobTitle()))
                .toList();
    }

    // ---- utilitaires ----

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Compte introuvable"));
    }

    private Employee findLinkableEmployee(Long employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable"));
        if (employee.getUser() != null) {
            throw new ConflictException("Cet employé est déjà associé à un autre compte");
        }
        return employee;
    }

    /** Empêche de retirer le dernier administrateur actif (verrouillage de la plateforme). */
    private void ensureAnotherActiveAdmin(User user) {
        if (user.isEnabled() && userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("Opération refusée : il doit rester au moins un administrateur actif");
        }
    }

    private static boolean isSelf(User user, String actorEmail) {
        return user.getEmail().equalsIgnoreCase(actorEmail);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static Specification<User> filter(String search, Role role, Boolean enabled) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.like(cb.lower(root.get("email")), pattern, '\\'));
            }
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    private static UserSummaryResponse toSummary(User u, Employee e) {
        return new UserSummaryResponse(u.getId(), u.getEmail(), u.getRole(), u.isEnabled(), u.getLastLoginAt(),
                u.getCreatedAt(), e != null ? e.getId() : null,
                e != null ? e.getFirstName() + " " + e.getLastName() : null);
    }
}
