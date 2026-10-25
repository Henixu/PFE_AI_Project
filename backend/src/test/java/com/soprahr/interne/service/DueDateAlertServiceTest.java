package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.Task;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.NotificationType;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.entity.enums.TaskStatus;
import com.soprahr.interne.repository.TaskRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DueDateAlertServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 25);

    @Mock TaskRepository tasks;
    @Mock NotificationPublisher notifier;

    private DueDateAlertService service;
    private User aliceUser;
    private Employee alice;

    @BeforeEach
    void setUp() {
        service = new DueDateAlertService(tasks, notifier, 1);
        aliceUser = TestData.user(3L, "alice@test.local", Role.EMPLOYEE);
        alice = TestData.employee(13L, "Alice", "Dupont", aliceUser, null);
        when(notifier.notifyOnce(any(), any(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void windowIncludesTomorrowAndOpenStatusesOnly() {
        service.generateTaskAlerts(TODAY);

        verify(tasks).findDueForAlert(EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS), TODAY.plusDays(1));
    }

    @Test
    void configuredWindowIsApplied() {
        new DueDateAlertService(tasks, notifier, 3).generateTaskAlerts(TODAY);

        verify(tasks).findDueForAlert(anyCollection(), eq(TODAY.plusDays(3)));
    }

    @Test
    void assigneeIsAlertedOncePerTaskAndDueDate() {
        when(tasks.findDueForAlert(anyCollection(), any())).thenReturn(List.of(task(1L, "Rapport", TODAY.plusDays(1))));

        assertEquals(1, service.generateTaskAlerts(TODAY));

        verify(notifier).notifyOnce(eq(aliceUser), eq(NotificationType.TASK_DUE), eq("Échéance proche"),
                contains("arrive à échéance le 26/10/2026"), eq("/tasks"), eq("TASK_DUE:1:2026-10-26"));
    }

    @Test
    void wordingDependsOnHowCloseTheDueDateIs() {
        when(tasks.findDueForAlert(anyCollection(), any())).thenReturn(List.of(
                task(1L, "Aujourd'hui", TODAY), task(2L, "Hier", TODAY.minusDays(1))));

        assertEquals(2, service.generateTaskAlerts(TODAY));

        verify(notifier).notifyOnce(any(), any(), eq("Échéance proche"), contains("arrive à échéance aujourd'hui"),
                any(), eq("TASK_DUE:1:2026-10-25"));
        verify(notifier).notifyOnce(any(), any(), eq("Tâche en retard"), contains("est en retard (échéance le 24/10/2026)"),
                any(), eq("TASK_DUE:2:2026-10-24"));
    }

    @Test
    void alreadyAlertedTasksAreNotCounted() {
        when(tasks.findDueForAlert(anyCollection(), any())).thenReturn(List.of(
                task(1L, "Déjà alertée", TODAY), task(2L, "Nouvelle", TODAY)));
        when(notifier.notifyOnce(any(), any(), any(), any(), any(), eq("TASK_DUE:1:2026-10-25"))).thenReturn(false);

        assertEquals(1, service.generateTaskAlerts(TODAY));
    }

    @Test
    void nothingToAlertCreatesNothing() {
        when(tasks.findDueForAlert(anyCollection(), any())).thenReturn(List.of());

        assertEquals(0, service.generateTaskAlerts(TODAY));
    }

    private Task task(long id, String title, LocalDate due) {
        Task t = new Task();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTitle(title);
        t.setDueDate(due);
        t.setAssignee(alice);
        return t;
    }
}
