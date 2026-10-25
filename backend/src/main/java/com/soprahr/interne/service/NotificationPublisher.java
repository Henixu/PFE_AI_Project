package com.soprahr.interne.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.entity.Employee;
import com.soprahr.interne.entity.Notification;
import com.soprahr.interne.entity.User;
import com.soprahr.interne.entity.enums.NotificationType;
import com.soprahr.interne.repository.NotificationRepository;

/**
 * Création des notifications, appelée par les autres services (pas d'API publique). S'exécute dans la
 * transaction de l'événement qui la déclenche : si celui-ci est annulé, la notification l'est aussi.
 */
@Service
@Transactional
public class NotificationPublisher {

    private static final int TITLE_MAX = 200;
    private static final int MESSAGE_MAX = 1000;
    private static final int LINK_MAX = 200;

    private final NotificationRepository notifications;

    public NotificationPublisher(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public Notification notify(User recipient, NotificationType type, String title, String message, String link) {
        return create(recipient, type, title, message, link, null);
    }

    /** Notifie le compte d'un employé ; sans effet si l'employé n'a pas de compte ou si celui-ci est désactivé. */
    public void notifyEmployee(Employee employee, NotificationType type, String title, String message, String link) {
        User user = employee == null ? null : employee.getUser();
        if (user != null && user.isEnabled()) {
            create(user, type, title, message, link, null);
        }
    }

    /** Crée la notification sauf si le destinataire a déjà reçu une alerte de même clé ; renvoie true si elle est créée. */
    public boolean notifyOnce(User recipient, NotificationType type, String title, String message, String link,
            String dedupKey) {
        Objects.requireNonNull(dedupKey, "dedupKey");
        if (notifications.existsByRecipientIdAndDedupKey(recipient.getId(), dedupKey)) {
            return false;
        }
        create(recipient, type, title, message, link, dedupKey);
        return true;
    }

    private Notification create(User recipient, NotificationType type, String title, String message, String link,
            String dedupKey) {
        Objects.requireNonNull(recipient, "recipient");
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type == null ? NotificationType.GENERAL : type);
        notification.setTitle(abbreviate(Objects.requireNonNull(title, "title"), TITLE_MAX));
        notification.setMessage(message == null ? null : abbreviate(message, MESSAGE_MAX));
        notification.setLink(link == null ? null : abbreviate(link, LINK_MAX));
        notification.setDedupKey(dedupKey);
        return notifications.save(notification);
    }

    private static String abbreviate(String value, int max) {
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }
}
