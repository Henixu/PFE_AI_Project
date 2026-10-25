package com.soprahr.interne.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.dto.NotificationResponse;
import com.soprahr.interne.dto.PageResponse;
import com.soprahr.interne.entity.Notification;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.exception.ResourceNotFoundException;
import com.soprahr.interne.repository.NotificationRepository;
import com.soprahr.interne.repository.UserRepository;

/** Boîte de notifications de l'utilisateur connecté : chacun ne voit et ne modifie que les siennes. */
@Service
@PreAuthorize("isAuthenticated()")
@Transactional
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notifications;
    private final UserRepository users;

    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(boolean unreadOnly, int page, int size, String actorEmail) {
        Long recipientId = recipient(actorEmail).getId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<Notification> result = unreadOnly
                ? notifications.findByRecipientIdAndReadFalse(recipientId, pageable)
                : notifications.findByRecipientId(recipientId, pageable);
        return PageResponse.of(result, NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String actorEmail) {
        return notifications.countByRecipientIdAndReadFalse(recipient(actorEmail).getId());
    }

    /** Persiste l'état de lecture ; sans effet (et sans changer la date de lecture) si déjà lue. */
    public NotificationResponse markRead(Long id, String actorEmail) {
        Notification notification = notifications.findByIdAndRecipientId(id, recipient(actorEmail).getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification introuvable: " + id));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(Instant.now());
        }
        return toResponse(notification);
    }

    /** Marque toutes les notifications non lues de l'utilisateur ; renvoie leur nombre. */
    public int markAllRead(String actorEmail) {
        return notifications.markAllRead(recipient(actorEmail).getId(), Instant.now());
    }

    private User recipient(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(() -> new ResourceNotFoundException("Compte introuvable"));
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(), n.isRead(),
                n.getCreatedAt(), n.getReadAt());
    }
}
