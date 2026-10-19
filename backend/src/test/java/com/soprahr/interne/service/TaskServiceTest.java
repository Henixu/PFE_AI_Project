package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import com.soprahr.interne.dto.EmployeeOptionResponse;
import com.soprahr.interne.dto.TaskRequest;
import com.soprahr.interne.dto.TaskResponse;
import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.Task;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.entity.enums.TaskPriority;
import com.soprahr.interne.entity.enums.TaskStatus;
import com.soprahr.interne.exception.BusinessRuleException;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.EmployeeRepository;
import com.soprahr.interne.repository.TaskRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {

    @Mock TaskRepository tasks;
    @Mock EmployeeRepository employees;
    @Mock ActorResolver actors;
    @Mock AuditService audit;

    private TaskService service;

    private User adminUser;
    private User managerUser;
    private User aliceUser;
    private User bobUser;
    private Employee manager;
    private Employee alice;
    private Employee bob;
    private Employee carol;

    @BeforeEach
    void setUp() {
        service = new TaskService(tasks, employees, actors, audit);
        adminUser = TestData.user(1L, "admin@test.local", Role.ADMIN);
        managerUser = TestData.user(2L, "manager@test.local", Role.MANAGER);
        aliceUser = TestData.user(3L, "alice@test.local", Role.EMPLOYEE);
        bobUser = TestData.user(4L, "bob@test.local", Role.EMPLOYEE);
        manager = TestData.employee(12L, "Karim", "Manager", managerUser, null);
        alice = TestData.employee(13L, "Alice", "Dupont", aliceUser, manager);
        bob = TestData.employee(14L, "Bob", "Martin", bobUser, null);       // hors de l'équipe de Karim
        carol = TestData.employee(15L, "Carol", "Zed", null, manager);       // collaboratrice désactivée
        carol.setActive(false);

        when(actors.resolve("admin@test.local")).thenReturn(new CurrentActor(adminUser, null));
        when(actors.resolve("manager@test.local")).thenReturn(new CurrentActor(managerUser, manager));
        when(actors.resolve("alice@test.local")).thenReturn(new CurrentActor(aliceUser, alice));
        when(actors.resolve("bob@test.local")).thenReturn(new CurrentActor(bobUser, bob));

        when(employees.findByManagerId(12L)).thenReturn(List.of(alice, carol));
        for (Employee e : List.of(manager, alice, bob, carol)) {
            when(employees.findById(e.getId())).thenReturn(Optional.of(e));
        }
        when(employees.findByActiveTrueOrderByLastNameAscFirstNameAsc()).thenReturn(List.of(alice, bob, manager));
        when(employees.findAllById(any())).thenAnswer(inv -> StreamSupport
                .stream(((Iterable<Long>) inv.getArgument(0)).spliterator(), false)
                .map(id -> List.of(manager, alice, bob, carol).stream().filter(e -> e.getId().equals(id)).findFirst()
                        .orElseThrow())
                .toList());
        when(tasks.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            if (t.getId() == null) {
                ReflectionTestUtils.setField(t, "id", 100L);
            }
            return t;
        });
    }

    // ---- création et affectation ----

    @Test
    void employeeCanCreateTaskAssignedToThemselves() {
        TaskResponse r = service.create(request(alice.getId(), LocalDate.now().plusDays(3)), "alice@test.local");

        assertEquals(TaskStatus.TODO, r.status());
        assertEquals("Alice Dupont", r.assigneeName());
        assertTrue(r.canEdit());
        verify(audit).record(any(), any(), any(), any(), any());
    }

    @Test
    void employeeCannotAssignToSomeoneElse() {
        assertThrows(AccessDeniedException.class,
                () -> service.create(request(bob.getId(), null), "alice@test.local"));
        verify(tasks, never()).save(any());
    }

    @Test
    void managerAssignsToDirectReportButNotOutsideTheTeam() {
        assertEquals(alice.getId(), service.create(request(alice.getId(), null), "manager@test.local").assigneeId());
        assertThrows(AccessDeniedException.class,
                () -> service.create(request(bob.getId(), null), "manager@test.local"));
    }

    @Test
    void cannotAssignToDeactivatedEmployee() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(request(carol.getId(), null), "manager@test.local"));
    }

    @Test
    void adminAssignsToAnyActiveEmployee() {
        assertEquals(bob.getId(), service.create(request(bob.getId(), null), "admin@test.local").assigneeId());
    }

    @Test
    void dueDateInThePastIsRejectedOnCreation() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(request(null, LocalDate.now().minusDays(1)), "alice@test.local"));
    }

    @Test
    void assigneeListMatchesRole() {
        assertEquals(List.of("Alice Dupont", "Bob Martin", "Karim Manager"),
                service.assignees("admin@test.local").stream().map(EmployeeOptionResponse::fullName).toList());
        // manager : lui-même + collaborateurs actifs, triés par nom (Carol est désactivée)
        assertEquals(List.of("Alice Dupont", "Karim Manager"),
                service.assignees("manager@test.local").stream().map(EmployeeOptionResponse::fullName).toList());
        assertEquals(List.of("Alice Dupont"),
                service.assignees("alice@test.local").stream().map(EmployeeOptionResponse::fullName).toList());
    }

    // ---- périmètre et modification ----

    @Test
    void taskOutsideTheScopeIsReportedAsNotFound() {
        Task task = task(1L, adminUser, bob, TaskStatus.TODO);
        when(tasks.findWithDetailsById(1L)).thenReturn(Optional.of(task));

        assertThrows(ResourceNotFoundException.class, () -> service.get(1L, "alice@test.local"));
        assertThrows(ResourceNotFoundException.class, () -> service.get(1L, "manager@test.local"));
        assertEquals(1L, service.get(1L, "admin@test.local").id());
    }

    @Test
    void assigneeWhoDidNotCreateTheTaskCannotEditItsFields() {
        Task task = task(1L, managerUser, alice, TaskStatus.TODO);
        when(tasks.findWithDetailsById(1L)).thenReturn(Optional.of(task));

        assertFalse(service.get(1L, "alice@test.local").canEdit());
        assertThrows(AccessDeniedException.class,
                () -> service.update(1L, request(alice.getId(), null), "alice@test.local"));
    }

    @Test
    void managerEditsAndReassignsTeamTask() {
        Task task = task(1L, adminUser, alice, TaskStatus.TODO);
        when(tasks.findWithDetailsById(1L)).thenReturn(Optional.of(task));

        TaskResponse r = service.update(1L, new TaskRequest("  Nouveau titre ", "d", TaskPriority.URGENT, null,
                manager.getId()), "manager@test.local");

        assertEquals("Nouveau titre", r.title());
        assertEquals(TaskPriority.URGENT, r.priority());
        assertEquals(manager.getId(), r.assigneeId());
    }

    @Test
    void updateKeepsAnUnchangedPastDueDateAndAssignee() {
        Task task = task(1L, managerUser, alice, TaskStatus.TODO);
        task.setDueDate(LocalDate.now().minusDays(5));
        when(tasks.findWithDetailsById(1L)).thenReturn(Optional.of(task));

        TaskResponse r = service.update(1L, new TaskRequest("Titre", null, TaskPriority.LOW,
                LocalDate.now().minusDays(5), alice.getId()), "manager@test.local");

        assertTrue(r.overdue());
    }

    // ---- statuts ----

    @Test
    void managerFollowsTheAllowedTransitions() {
        Task task = task(1L, managerUser, alice, TaskStatus.TODO);
        when(tasks.findWithDetailsById(1L)).thenReturn(Optional.of(task));

        assertEquals(List.of(TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
                service.get(1L, "manager@test.local").allowedStatuses());
        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(1L, TaskStatus.DONE, "manager@test.local"));

        assertEquals(TaskStatus.IN_PROGRESS, service.changeStatus(1L, TaskStatus.IN_PROGRESS, "manager@test.local").status());
        assertEquals(TaskStatus.DONE, service.changeStatus(1L, TaskStatus.DONE, "manager@test.local").status());
        assertEquals(TaskStatus.IN_PROGRESS, service.changeStatus(1L, TaskStatus.IN_PROGRESS, "manager@test.local").status());
    }

    @Test
    void assigneeAdvancesTheirTaskButCannotCancelNorReopen() {
        Task task = task(1L, managerUser, alice, TaskStatus.IN_PROGRESS);
        when(tasks.findWithDetailsById(1L)).thenReturn(Optional.of(task));

        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(1L, TaskStatus.CANCELLED, "alice@test.local"));
        assertEquals(TaskStatus.DONE, service.changeStatus(1L, TaskStatus.DONE, "alice@test.local").status());
        assertThrows(BusinessRuleException.class,
                () -> service.changeStatus(1L, TaskStatus.IN_PROGRESS, "alice@test.local"));
        assertTrue(service.get(1L, "alice@test.local").allowedStatuses().isEmpty());
    }

    @Test
    void settingTheSameStatusIsANoOp() {
        Task task = task(1L, managerUser, alice, TaskStatus.TODO);
        when(tasks.findWithDetailsById(1L)).thenReturn(Optional.of(task));

        assertEquals(TaskStatus.TODO, service.changeStatus(1L, TaskStatus.TODO, "alice@test.local").status());
        verify(audit, never()).record(any(), any(), any(), any(), any());
    }

    // ---- retard ----

    @Test
    void overdueMeansPastDueDateAndNotFinished() {
        LocalDate today = LocalDate.of(2026, 10, 19);
        assertTrue(TaskService.isOverdue(dated(today.minusDays(1), TaskStatus.TODO), today));
        assertTrue(TaskService.isOverdue(dated(today.minusDays(1), TaskStatus.IN_PROGRESS), today));
        assertFalse(TaskService.isOverdue(dated(today, TaskStatus.TODO), today));
        assertFalse(TaskService.isOverdue(dated(today.minusDays(1), TaskStatus.DONE), today));
        assertFalse(TaskService.isOverdue(dated(today.minusDays(1), TaskStatus.CANCELLED), today));
        assertFalse(TaskService.isOverdue(dated(null, TaskStatus.TODO), today));
    }

    // ---- utilitaires ----

    private static TaskRequest request(Long assigneeId, LocalDate dueDate) {
        return new TaskRequest("Titre", "Description", TaskPriority.MEDIUM, dueDate, assigneeId);
    }

    private static Task task(long id, User creator, Employee assignee, TaskStatus status) {
        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", id);
        task.setTitle("Tâche " + id);
        task.setCreatedBy(creator);
        task.setAssignee(assignee);
        task.setStatus(status);
        return task;
    }

    private static Task dated(LocalDate dueDate, TaskStatus status) {
        Task task = new Task();
        task.setDueDate(dueDate);
        task.setStatus(status);
        return task;
    }
}
