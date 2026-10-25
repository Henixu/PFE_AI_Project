package com.soprahr.interne.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.dto.EmployeeOptionResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.dto.TaskRequest;
import com.soprahr.interne.dto.TaskResponse;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.Task;
import com.soprahr.interne.entity.enums.NotificationType;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.entity.enums.TaskPriority;
import com.soprahr.interne.entity.enums.TaskStatus;
import com.soprahr.interne.exception.BusinessRuleException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.EmployeeRepository;
import com.soprahr.interne.repository.TaskRepository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/**
 * Gestion des tâches. Périmètre d'accès :
 * <ul>
 * <li>ADMIN, HR : toutes les tâches, affectables à tout employé actif ;</li>
 * <li>MANAGER : les tâches qu'il a créées, ou affectées à lui-même ou à ses collaborateurs directs ;</li>
 * <li>EMPLOYEE : les tâches qu'il a créées ou qui lui sont affectées (il ne s'affecte que lui-même).</li>
 * </ul>
 * Modifier les champs d'une tâche est réservé à ceux qui la gèrent (ADMIN, HR, créateur, manager du périmètre) ;
 * le responsable d'une tâche peut seulement en faire avancer le statut.
 */
@Service
@PreAuthorize("isAuthenticated()")
@Transactional
public class TaskService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("dueDate", "createdAt", "updatedAt", "title");
    private static final Set<TaskStatus> OPEN = EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Transitions autorisées pour qui gère la tâche. */
    private static final Map<TaskStatus, Set<TaskStatus>> TRANSITIONS = Map.of(
            TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.TODO, TaskStatus.DONE, TaskStatus.CANCELLED),
            TaskStatus.DONE, EnumSet.of(TaskStatus.IN_PROGRESS),
            TaskStatus.CANCELLED, EnumSet.of(TaskStatus.TODO));

    /** Critères de la liste ; tout critère à null est ignoré. */
    public record Filter(String search, Long assigneeId, TaskPriority priority, TaskStatus status, Boolean overdue,
            Boolean mine, LocalDate dueFrom, LocalDate dueTo) {
    }

    /** Droits de l'utilisateur connecté : compte, et employés dont il voit les tâches (lui + son équipe). */
    private record Access(CurrentActor actor, Set<Long> scope) {
        boolean unrestricted() {
            return actor.isAdminOrHr();
        }
    }

    private final TaskRepository tasks;
    private final EmployeeRepository employees;
    private final ActorResolver actors;
    private final AuditService audit;
    private final NotificationPublisher notifier;

    public TaskService(TaskRepository tasks, EmployeeRepository employees, ActorResolver actors, AuditService audit,
            NotificationPublisher notifier) {
        this.tasks = tasks;
        this.employees = employees;
        this.actors = actors;
        this.audit = audit;
        this.notifier = notifier;
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(Filter filter, int page, int size, String sortBy, String direction,
            String actorEmail) {
        Access access = access(actorEmail);
        LocalDate today = LocalDate.now();
        Sort.Direction dir = "desc".equalsIgnoreCase(direction) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(new Sort.Order(dir, SORTABLE.contains(sortBy) ? sortBy : "dueDate"),
                        new Sort.Order(Sort.Direction.ASC, "id")));
        Page<Task> result = tasks.findAll(matching(filter, access, today), pageable);
        return PageResponse.of(result, result.getContent().stream().map(t -> toResponse(t, access, today)).toList());
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id, String actorEmail) {
        Access access = access(actorEmail);
        return toResponse(findVisible(id, access), access, LocalDate.now());
    }

    public TaskResponse create(TaskRequest request, String actorEmail) {
        Access access = access(actorEmail);
        LocalDate today = LocalDate.now();
        assertDueDateNotPast(request.dueDate(), today);

        Task task = new Task();
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        task.setAssignee(resolveAssignee(request.assigneeId(), access));
        task.setCreatedBy(access.actor().user());
        task = tasks.save(task);
        audit.record(actorEmail, "TASK_CREATE", "Task", task.getId(), "title=" + task.getTitle()
                + (task.getAssignee() != null ? ", assigneeId=" + task.getAssignee().getId() : ""));
        notifyAssignment(task, access.actor());
        return toResponse(task, access, today);
    }

    public TaskResponse update(Long id, TaskRequest request, String actorEmail) {
        Access access = access(actorEmail);
        LocalDate today = LocalDate.now();
        Task task = findVisible(id, access);
        if (!canManage(task, access)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à modifier cette tâche");
        }
        if (!Objects.equals(request.dueDate(), task.getDueDate())) {
            assertDueDateNotPast(request.dueDate(), today);
        }
        Long currentAssignee = task.getAssignee() == null ? null : task.getAssignee().getId();
        boolean reassigned = !Objects.equals(request.assigneeId(), currentAssignee);
        if (reassigned) {
            task.setAssignee(resolveAssignee(request.assigneeId(), access));
        }
        task.setTitle(request.title().trim());
        task.setDescription(blankToNull(request.description()));
        task.setPriority(request.priority());
        task.setDueDate(request.dueDate());
        audit.record(actorEmail, "TASK_UPDATE", "Task", id, "title=" + task.getTitle()
                + ", priority=" + task.getPriority()
                + (reassigned ? ", assigneeId=" + request.assigneeId() : ""));
        if (reassigned) {
            notifyAssignment(task, access.actor());
        }
        return toResponse(task, access, today);
    }

    public TaskResponse changeStatus(Long id, TaskStatus newStatus, String actorEmail) {
        Access access = access(actorEmail);
        Task task = findVisible(id, access);
        if (!canManage(task, access) && !isAssignee(task, access)) {
            throw new AccessDeniedException("Vous n'êtes pas autorisé à changer le statut de cette tâche");
        }
        TaskStatus previous = task.getStatus();
        if (previous != newStatus) {
            if (!allowedStatuses(task, access).contains(newStatus)) {
                throw new BusinessRuleException("Passage du statut " + previous + " à " + newStatus + " non autorisé");
            }
            task.setStatus(newStatus);
            audit.record(actorEmail, "TASK_STATUS", "Task", id, previous + " -> " + newStatus);
        }
        return toResponse(task, access, LocalDate.now());
    }

    /** Employés auxquels l'utilisateur connecté a le droit d'affecter une tâche. */
    @Transactional(readOnly = true)
    public List<EmployeeOptionResponse> assignees(String actorEmail) {
        return assignable(access(actorEmail)).stream()
                .map(e -> new EmployeeOptionResponse(e.getId(), e.getEmployeeNumber(),
                        e.getFirstName() + " " + e.getLastName(), e.getJobTitle()))
                .toList();
    }

    /** Informe le nouveau responsable (sauf s'il s'est affecté la tâche lui-même). */
    private void notifyAssignment(Task task, CurrentActor actor) {
        Employee assignee = task.getAssignee();
        if (assignee == null || assignee.getId().equals(actor.employeeId())) {
            return;
        }
        String assigner = actor.employee() == null ? actor.user().getEmail()
                : actor.employee().getFirstName() + " " + actor.employee().getLastName();
        String due = task.getDueDate() == null ? "" : " Échéance : " + task.getDueDate().format(DAY) + ".";
        notifier.notifyEmployee(assignee, NotificationType.TASK_ASSIGNED, "Nouvelle tâche affectée",
                assigner + " vous a affecté la tâche « " + task.getTitle() + " »." + due, "/tasks");
    }

    // ---- accès ----

    private Access access(String email) {
        CurrentActor actor = actors.resolve(email);
        Set<Long> scope = new HashSet<>();
        if (actor.employeeId() != null) {
            scope.add(actor.employeeId());
            if (actor.role() == Role.MANAGER) {
                employees.findByManagerId(actor.employeeId()).forEach(e -> scope.add(e.getId()));
            }
        }
        return new Access(actor, scope);
    }

    private Task findVisible(Long id, Access access) {
        Task task = tasks.findWithDetailsById(id).orElseThrow(() -> notFound(id));
        if (!canView(task, access)) {
            throw notFound(id); // ne pas révéler l'existence d'une tâche hors périmètre
        }
        return task;
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Tâche introuvable: " + id);
    }

    private boolean canView(Task task, Access access) {
        return access.unrestricted() || isCreator(task, access)
                || (task.getAssignee() != null && access.scope().contains(task.getAssignee().getId()));
    }

    private boolean canManage(Task task, Access access) {
        if (access.unrestricted() || isCreator(task, access)) {
            return true;
        }
        return access.actor().role() == Role.MANAGER && task.getAssignee() != null
                && access.scope().contains(task.getAssignee().getId());
    }

    private static boolean isCreator(Task task, Access access) {
        return task.getCreatedBy().getId().equals(access.actor().user().getId());
    }

    private static boolean isAssignee(Task task, Access access) {
        return task.getAssignee() != null && access.actor().employeeId() != null
                && task.getAssignee().getId().equals(access.actor().employeeId());
    }

    /** Statuts atteignables : le responsable simple ne peut ni annuler ni rouvrir une tâche close. */
    private List<TaskStatus> allowedStatuses(Task task, Access access) {
        Set<TaskStatus> next = TRANSITIONS.get(task.getStatus());
        if (canManage(task, access)) {
            return List.copyOf(next);
        }
        if (isAssignee(task, access) && OPEN.contains(task.getStatus())) {
            return next.stream().filter(s -> s != TaskStatus.CANCELLED).toList();
        }
        return List.of();
    }

    private List<Employee> assignable(Access access) {
        if (access.unrestricted()) {
            return employees.findByActiveTrueOrderByLastNameAscFirstNameAsc();
        }
        if (access.scope().isEmpty()) {
            return List.of();
        }
        return employees.findAllById(access.scope()).stream().filter(Employee::isActive)
                .sorted(Comparator.comparing(Employee::getLastName).thenComparing(Employee::getFirstName)).toList();
    }

    private Employee resolveAssignee(Long assigneeId, Access access) {
        if (assigneeId == null) {
            return null;
        }
        Employee employee = employees.findById(assigneeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employé introuvable: " + assigneeId));
        if (!employee.isActive()) {
            throw new BusinessRuleException("Impossible d'affecter une tâche à un employé désactivé");
        }
        if (!access.unrestricted() && !access.scope().contains(assigneeId)) {
            throw new AccessDeniedException("Vous ne pouvez affecter une tâche qu'à vous-même ou à votre équipe");
        }
        return employee;
    }

    // ---- règles et projection ----

    private static void assertDueDateNotPast(LocalDate dueDate, LocalDate today) {
        if (dueDate != null && dueDate.isBefore(today)) {
            throw new BusinessRuleException("L'échéance ne peut pas être dans le passé");
        }
    }

    static boolean isOverdue(Task task, LocalDate today) {
        return task.getDueDate() != null && task.getDueDate().isBefore(today) && OPEN.contains(task.getStatus());
    }

    private TaskResponse toResponse(Task t, Access access, LocalDate today) {
        Employee assignee = t.getAssignee();
        return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getPriority(), t.getStatus(),
                t.getDueDate(), isOverdue(t, today), assignee == null ? null : assignee.getId(),
                assignee == null ? null : assignee.getFirstName() + " " + assignee.getLastName(),
                t.getCreatedBy().getEmail(), t.getCreatedAt(), t.getUpdatedAt(), canManage(t, access),
                allowedStatuses(t, access));
    }

    private Specification<Task> matching(Filter filter, Access access, LocalDate today) {
        String pattern = SqlLike.contains(filter.search());
        return (root, query, cb) -> {
            Join<Task, Employee> assignee = root.join("assignee", JoinType.LEFT);
            List<Predicate> predicates = new ArrayList<>();
            if (!access.unrestricted()) {
                Predicate own = cb.equal(root.get("createdBy").get("id"), access.actor().user().getId());
                predicates.add(access.scope().isEmpty() ? own : cb.or(own, assignee.get("id").in(access.scope())));
            }
            if (pattern != null) {
                predicates.add(cb.or(cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), pattern, '\\')));
            }
            if (filter.assigneeId() != null) {
                predicates.add(cb.equal(assignee.get("id"), filter.assigneeId()));
            }
            if (Boolean.TRUE.equals(filter.mine())) {
                predicates.add(access.actor().employeeId() == null
                        ? cb.disjunction()
                        : cb.equal(assignee.get("id"), access.actor().employeeId()));
            }
            if (filter.priority() != null) {
                predicates.add(cb.equal(root.get("priority"), filter.priority()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (Boolean.TRUE.equals(filter.overdue())) {
                predicates.add(cb.and(cb.lessThan(root.<LocalDate>get("dueDate"), today), root.get("status").in(OPEN)));
            }
            if (filter.dueFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("dueDate"), filter.dueFrom()));
            }
            if (filter.dueTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("dueDate"), filter.dueTo()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
