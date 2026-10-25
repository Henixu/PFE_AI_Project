package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.Notification;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.NotificationType;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationPublisherTest {

    @Mock NotificationRepository notifications;

    private NotificationPublisher publisher;
    private User alice;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher(notifications);
        alice = TestData.user(3L, "alice@test.local", Role.EMPLOYEE);
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void notificationIsAttachedToTheRecipient() {
        Notification n = publisher.notify(alice, NotificationType.TASK_ASSIGNED, " Titre ", "Message", "/tasks");

        assertSame(alice, n.getRecipient());
        assertEquals(NotificationType.TASK_ASSIGNED, n.getType());
        assertEquals("Titre", n.getTitle());
        assertEquals("Message", n.getMessage());
        assertEquals("/tasks", n.getLink());
        assertFalse(n.isRead());
        assertNull(n.getDedupKey());
    }

    @Test
    void typeDefaultsToGeneralAndMessageIsOptional() {
        Notification n = publisher.notify(alice, null, "Titre", null, null);

        assertEquals(NotificationType.GENERAL, n.getType());
        assertNull(n.getMessage());
        assertNull(n.getLink());
    }

    @Test
    void overlongTextsAreTruncatedToTheColumnSizes() {
        Notification n = publisher.notify(alice, null, "t".repeat(300), "m".repeat(2000), null);

        assertEquals(200, n.getTitle().length());
        assertEquals(1000, n.getMessage().length());
        assertTrue(n.getTitle().endsWith("…"));
    }

    @Test
    void recipientAndTitleAreRequired() {
        assertThrows(NullPointerException.class, () -> publisher.notify(null, null, "Titre", null, null));
        assertThrows(NullPointerException.class, () -> publisher.notify(alice, null, null, null, null));
        verify(notifications, never()).save(any());
    }

    @Test
    void employeeNotificationGoesToTheLinkedAccount() {
        Employee employee = TestData.employee(13L, "Alice", "Dupont", alice, null);

        publisher.notifyEmployee(employee, NotificationType.LEAVE_DECISION, "Congé approuvé", "ok", "/leaves");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertSame(alice, saved.getValue().getRecipient());
    }

    @Test
    void employeeWithoutActiveAccountIsSilentlySkipped() {
        User disabled = TestData.user(5L, "off@test.local", Role.EMPLOYEE);
        disabled.setEnabled(false);

        publisher.notifyEmployee(null, null, "T", null, null);
        publisher.notifyEmployee(TestData.employee(1L, "A", "B", null, null), null, "T", null, null);
        publisher.notifyEmployee(TestData.employee(2L, "C", "D", disabled, null), null, "T", null, null);

        verify(notifications, never()).save(any());
    }

    @Test
    void notifyOnceCreatesTheAlertOnlyTheFirstTime() {
        when(notifications.existsByRecipientIdAndDedupKey(3L, "TASK_DUE:1:2026-10-30")).thenReturn(false, true);

        assertTrue(publisher.notifyOnce(alice, NotificationType.TASK_DUE, "T", "M", "/tasks", "TASK_DUE:1:2026-10-30"));
        assertFalse(publisher.notifyOnce(alice, NotificationType.TASK_DUE, "T", "M", "/tasks", "TASK_DUE:1:2026-10-30"));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertEquals("TASK_DUE:1:2026-10-30", saved.getValue().getDedupKey());
    }
}
