package com.soprahr.interne.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.soprahr.interne.dto.NotificationResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.Notification;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.NotificationType;
import com.soprahr.interne.entity.enums.Role;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.NotificationRepository;
import com.soprahr.interne.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock NotificationRepository notifications;
    @Mock UserRepository users;

    private NotificationService service;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notifications, users);
        alice = TestData.user(3L, "alice@test.local", Role.EMPLOYEE);
        bob = TestData.user(4L, "bob@test.local", Role.EMPLOYEE);
        when(users.findByEmailIgnoreCase("alice@test.local")).thenReturn(Optional.of(alice));
        when(users.findByEmailIgnoreCase("bob@test.local")).thenReturn(Optional.of(bob));
    }

    @Test
    void listReturnsOnlyTheCallersNotificationsNewestFirst() {
        Notification n = notification(1L, alice, false);
        when(notifications.findByRecipientId(eq(3L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(n), PageRequest.of(0, 10), 1));

        PageResponse<NotificationResponse> r = service.list(false, 0, 10, "alice@test.local");

        assertEquals(1, r.totalElements());
        assertEquals("Titre", r.content().get(0).title());
        assertEquals(NotificationType.TASK_ASSIGNED, r.content().get(0).type());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notifications).findByRecipientId(eq(3L), pageable.capture());
        assertEquals("createdAt: DESC,id: DESC", pageable.getValue().getSort().toString());
        verify(notifications, never()).findByRecipientId(eq(4L), any());
    }

    @Test
    void unreadOnlyFiltersOnTheReadState() {
        when(notifications.findByRecipientIdAndReadFalse(eq(3L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification(1L, alice, false)), PageRequest.of(0, 10), 1));

        assertEquals(1, service.list(true, 0, 10, "alice@test.local").content().size());
        verify(notifications, never()).findByRecipientId(any(), any());
    }

    @Test
    void pageSizeIsCapped() {
        when(notifications.findByRecipientId(eq(3L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        service.list(false, -3, 5000, "alice@test.local");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(notifications).findByRecipientId(eq(3L), pageable.capture());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals(0, pageable.getValue().getPageNumber());
    }

    @Test
    void unreadCountIsScopedToTheCaller() {
        when(notifications.countByRecipientIdAndReadFalse(3L)).thenReturn(4L);
        when(notifications.countByRecipientIdAndReadFalse(4L)).thenReturn(0L);

        assertEquals(4L, service.unreadCount("alice@test.local"));
        assertEquals(0L, service.unreadCount("bob@test.local"));
    }

    @Test
    void markReadPersistsTheReadStateWithItsDate() {
        Notification n = notification(1L, alice, false);
        when(notifications.findByIdAndRecipientId(1L, 3L)).thenReturn(Optional.of(n));

        NotificationResponse r = service.markRead(1L, "alice@test.local");

        assertTrue(r.read());
        assertTrue(n.isRead());
        assertNotNull(n.getReadAt());
    }

    @Test
    void markReadKeepsTheOriginalReadDate() {
        Notification n = notification(1L, alice, true);
        Instant first = Instant.parse("2026-10-01T08:00:00Z");
        n.setReadAt(first);
        when(notifications.findByIdAndRecipientId(1L, 3L)).thenReturn(Optional.of(n));

        service.markRead(1L, "alice@test.local");

        assertEquals(first, n.getReadAt());
    }

    @Test
    void anotherUsersNotificationIsReportedAsNotFound() {
        // la recherche est bornée au destinataire : pour Bob, la notification d'Alice n'existe pas
        when(notifications.findByIdAndRecipientId(1L, 3L)).thenReturn(Optional.of(notification(1L, alice, false)));
        when(notifications.findByIdAndRecipientId(1L, 4L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.markRead(1L, "bob@test.local"));
    }

    @Test
    void markAllReadOnlyTouchesTheCallersNotifications() {
        when(notifications.markAllRead(eq(3L), any(Instant.class))).thenReturn(2);

        assertEquals(2, service.markAllRead("alice@test.local"));
        verify(notifications, never()).markAllRead(eq(4L), any());
    }

    @Test
    void unknownAccountIsRejected() {
        assertThrows(ResourceNotFoundException.class, () -> service.unreadCount("ghost@test.local"));
    }

    @Test
    void newNotificationsStartUnread() {
        Notification n = new Notification();
        assertFalse(n.isRead());
        assertNull(n.getReadAt());
    }

    private static Notification notification(long id, User recipient, boolean read) {
        Notification n = new Notification();
        ReflectionTestUtils.setField(n, "id", id);
        ReflectionTestUtils.setField(n, "createdAt", Instant.parse("2026-10-02T09:00:00Z"));
        n.setRecipient(recipient);
        n.setType(NotificationType.TASK_ASSIGNED);
        n.setTitle("Titre");
        n.setMessage("Message");
        n.setLink("/tasks");
        n.setRead(read);
        return n;
    }
}
