package com.soprahr.interne.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.dto.LeaveResponse;
import com.soprahr.interne.dto.LeaveSubmitRequest;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.LeaveRequest;
import com.soprahr.interne.entity.enums.LeaveStatus;
import com.soprahr.interne.entity.enums.LeaveType;
import com.soprahr.interne.entity.enums.NotificationType;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.exception.BusinessRuleException;
import com.soprahr.interne.exception.ConflictException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.EmployeeRepository;
import com.soprahr.interne.repository.LeaveRequestRepository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

/**
 * Gestion des congés.
 * <ul>
 * <li>Tout employé rattaché à un compte dépose et consulte ses propres demandes (créées en PENDING).</li>
 * <li>ADMIN et HR traitent les demandes de tous ; un MANAGER traite celles de ses collaborateurs directs.</li>
 * <li>Personne ne traite sa propre demande, et une demande n'est décidée qu'une fois : la décision
 * (auteur, date, commentaire) reste ensuite figée sur la demande et dans le journal d'audit.</li>
 * <li>Deux demandes PENDING ou APPROVED d'un même employé ne peuvent pas se chevaucher.</li>
 * </ul>
 */
@Service
@PreAuthorize("isAuthenticated()")
@Transactional
public class LeaveService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("startDate", "endDate", "createdAt", "status");
    /** Statuts qui « occupent » la période pour la détection des chevauchements. */
    private static final Set<LeaveStatus> BLOCKING = EnumSet.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeaveRequestRepository leaves;
    private final EmployeeRepository employees;
    private final ActorResolver actors;
    private final AuditService audit;
    private final NotificationPublisher notifier;

    public LeaveService(LeaveRequestRepository leaves, EmployeeRepository employees, ActorResolver actors,
            AuditService audit, NotificationPublisher notifier) {
        this.leaves = leaves;
        this.employees = employees;
        this.actors = actors;
        this.audit = audit;
        this.notifier = notifier;
    }

    public LeaveResponse submit(LeaveSubmitRequest request, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        Employee employee = actor.employee();
        if (employee == null) {
            throw new BusinessRuleException("Aucun employé n'est associé à votre compte : dépôt de demande impossible");
        }
        if (!employee.isActive()) {
            throw new BusinessRuleException("Votre fiche employé est désactivée : dépôt de demande impossible");
        }
        assertValidDates(request.startDate(), request.endDate());
        if (leaves.existsOverlap(employee.getId(), BLOCKING, request.startDate(), request.endDate())) {
            throw new ConflictException(
                    "Une demande en attente ou approuvée couvre déjà tout ou partie de cette période");
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setType(request.type() == null ? LeaveType.ANNUAL : request.type());
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setReason(request.reason().trim());
        leave.setStatus(LeaveStatus.PENDING);
        leave = leaves.save(leave);
        audit.record(actorEmail, "LEAVE_SUBMIT", "LeaveRequest", leave.getId(),
                leave.getType() + " " + leave.getStartDate() + " -> " + leave.getEndDate());
        return toResponse(leave, actor, Map.of());
    }

    @Transactional(readOnly = true)
    public PageResponse<LeaveResponse> listMine(LeaveStatus status, int page, int size, String sortBy,
            String direction, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        Pageable pageable = pageable(page, size, sortBy, direction);
        if (actor.employeeId() == null) {
            return PageResponse.of(Page.<LeaveRequest>empty(pageable), List.<LeaveResponse>of());
        }
        Long employeeId = actor.employeeId();
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("employee").get("id"), employeeId));
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return toPage(leaves.findAll(spec, pageable), actor);
    }

    /** Demandes dans le périmètre de décision : tous les employés (ADMIN, HR) ou les collaborateurs directs (MANAGER). */
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    @Transactional(readOnly = true)
    public PageResponse<LeaveResponse> listToProcess(LeaveStatus status, Long employeeId, String search, int page,
            int size, String sortBy, String direction, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        Pageable pageable = pageable(page, size, sortBy, direction);
        if (!actor.isAdminOrHr() && actor.employeeId() == null) {
            return PageResponse.of(Page.<LeaveRequest>empty(pageable), List.<LeaveResponse>of());
        }
        String pattern = SqlLike.contains(search);
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            Join<LeaveRequest, Employee> employee = root.join("employee");
            List<Predicate> predicates = new ArrayList<>();
            if (!actor.isAdminOrHr()) {
                predicates.add(cb.equal(employee.get("manager").get("id"), actor.employeeId()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (employeeId != null) {
                predicates.add(cb.equal(employee.get("id"), employeeId));
            }
            if (pattern != null) {
                var fullName = cb.lower(cb.concat(cb.concat(employee.<String>get("firstName"), " "),
                        employee.<String>get("lastName")));
                predicates.add(cb.or(cb.like(fullName, pattern, '\\'),
                        cb.like(cb.lower(employee.<String>get("employeeNumber")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return toPage(leaves.findAll(spec, pageable), actor);
    }

    @Transactional(readOnly = true)
    public LeaveResponse get(Long id, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        LeaveRequest leave = leaves.findWithDetailsById(id).orElseThrow(() -> notFound(id));
        if (!isOwner(leave, actor) && !inDecisionScope(leave, actor)) {
            throw notFound(id);
        }
        return toResponse(leave, actor, decidedByNames(List.of(leave)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public LeaveResponse approve(Long id, String comment, String actorEmail) {
        return decide(id, LeaveStatus.APPROVED, comment, actorEmail);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'MANAGER')")
    public LeaveResponse reject(Long id, String comment, String actorEmail) {
        return decide(id, LeaveStatus.REJECTED, comment, actorEmail);
    }

    /** L'employé retire sa propre demande tant qu'elle n'a pas été traitée. */
    public LeaveResponse cancel(Long id, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        LeaveRequest leave = leaves.findForUpdateById(id).orElseThrow(() -> notFound(id));
        if (!isOwner(leave, actor)) {
            throw notFound(id);
        }
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("Seule une demande en attente peut être annulée");
        }
        leave.setStatus(LeaveStatus.CANCELLED);
        audit.record(actorEmail, "LEAVE_CANCEL", "LeaveRequest", id, "employeeId=" + leave.getEmployee().getId());
        return toResponse(leave, actor, Map.of());
    }

    private LeaveResponse decide(Long id, LeaveStatus decision, String comment, String actorEmail) {
        CurrentActor actor = actors.resolve(actorEmail);
        LeaveRequest leave = leaves.findForUpdateById(id).orElseThrow(() -> notFound(id));
        if (isOwner(leave, actor)) {
            throw new BusinessRuleException("Vous ne pouvez pas traiter votre propre demande");
        }
        if (!inDecisionScope(leave, actor)) {
            throw new AccessDeniedException("Vous n'êtes pas habilité à traiter cette demande");
        }
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new ConflictException("Cette demande a déjà été traitée (" + leave.getStatus() + ")");
        }
        String trimmed = comment == null || comment.isBlank() ? null : comment.trim();
        if (decision == LeaveStatus.REJECTED && trimmed == null) {
            throw new BusinessRuleException("Un motif est obligatoire pour refuser une demande");
        }
        leave.setStatus(decision);
        leave.setDecidedBy(actor.user());
        leave.setDecidedAt(Instant.now());
        leave.setDecisionComment(trimmed);
        audit.record(actorEmail, decision == LeaveStatus.APPROVED ? "LEAVE_APPROVE" : "LEAVE_REJECT",
                "LeaveRequest", id, "decision=" + decision + ", employeeId=" + leave.getEmployee().getId());
        boolean approved = decision == LeaveStatus.APPROVED;
        notifier.notifyEmployee(leave.getEmployee(), NotificationType.LEAVE_DECISION,
                approved ? "Congé approuvé" : "Congé refusé",
                "Votre demande de congé du " + leave.getStartDate().format(DAY) + " au "
                        + leave.getEndDate().format(DAY) + " a été " + (approved ? "approuvée." : "refusée.")
                        + (trimmed == null ? "" : " Commentaire : " + trimmed),
                "/leaves");
        return toResponse(leave, actor, decidedByNames(List.of(leave)));
    }

    // ---- règles d'accès ----

    private static boolean isOwner(LeaveRequest leave, CurrentActor actor) {
        return actor.employeeId() != null && leave.getEmployee().getId().equals(actor.employeeId());
    }

    /** ADMIN et HR voient tout ; un manager voit les demandes de ses collaborateurs directs. */
    private static boolean inDecisionScope(LeaveRequest leave, CurrentActor actor) {
        if (actor.isAdminOrHr()) {
            return true;
        }
        Employee manager = leave.getEmployee().getManager();
        return actor.role() == Role.MANAGER && actor.employeeId() != null && manager != null
                && manager.getId().equals(actor.employeeId());
    }

    private static boolean canDecide(LeaveRequest leave, CurrentActor actor) {
        return leave.getStatus() == LeaveStatus.PENDING && !isOwner(leave, actor) && inDecisionScope(leave, actor);
    }

    private static void assertValidDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BusinessRuleException("La date de fin doit être postérieure ou égale à la date de début");
        }
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Demande de congé introuvable: " + id);
    }

    // ---- projection ----

    private Pageable pageable(int page, int size, String sortBy, String direction) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(new Sort.Order("asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC,
                        SORTABLE.contains(sortBy) ? sortBy : "createdAt"), new Sort.Order(Sort.Direction.DESC, "id")));
    }

    private PageResponse<LeaveResponse> toPage(Page<LeaveRequest> page, CurrentActor actor) {
        Map<Long, String> names = decidedByNames(page.getContent());
        return PageResponse.of(page, page.getContent().stream().map(l -> toResponse(l, actor, names)).toList());
    }

    /** Nom de l'auteur de chaque décision (employé lié au compte) en une seule requête ; l'e-mail à défaut. */
    private Map<Long, String> decidedByNames(List<LeaveRequest> content) {
        List<Long> userIds = content.stream().filter(l -> l.getDecidedBy() != null)
                .map(l -> l.getDecidedBy().getId()).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return employees.findByUserIdIn(userIds).stream().collect(Collectors.toMap(e -> e.getUser().getId(),
                e -> e.getFirstName() + " " + e.getLastName(), (a, b) -> a));
    }

    private LeaveResponse toResponse(LeaveRequest l, CurrentActor actor, Map<Long, String> decidedByNames) {
        Employee e = l.getEmployee();
        String decidedBy = l.getDecidedBy() == null ? null
                : decidedByNames.getOrDefault(l.getDecidedBy().getId(), l.getDecidedBy().getEmail());
        return new LeaveResponse(l.getId(), e.getId(), e.getFirstName() + " " + e.getLastName(), e.getEmployeeNumber(),
                e.getDepartment() == null ? null : e.getDepartment().getName(), l.getType(), l.getStartDate(),
                l.getEndDate(), ChronoUnit.DAYS.between(l.getStartDate(), l.getEndDate()) + 1, l.getReason(),
                l.getStatus(), l.getCreatedAt(), decidedBy, l.getDecidedAt(), l.getDecisionComment(),
                canDecide(l, actor), isOwner(l, actor) && l.getStatus() == LeaveStatus.PENDING);
    }
}
