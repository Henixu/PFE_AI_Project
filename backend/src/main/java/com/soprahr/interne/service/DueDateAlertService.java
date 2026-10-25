package com.soprahr.interne.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.soprahr.interne.entity.Task;
import com.soprahr.interne.entity.enums.NotificationType;
import com.soprahr.interne.entity.enums.TaskStatus;
import com.soprahr.interne.repository.TaskRepository;

/**
 * Alertes d'échéance : notifie le responsable de chaque tâche ouverte dont l'échéance tombe dans la fenêtre
 * (aujourd'hui, les prochains jours, ou déjà dépassée). Une tâche n'alerte qu'une fois par échéance :
 * relancer le traitement ne crée pas de doublon, et reporter l'échéance réarme l'alerte.
 */
@Service
@Transactional
public class DueDateAlertService {

    private static final Set<TaskStatus> OPEN = EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TaskRepository tasks;
    private final NotificationPublisher notifier;
    private final int daysAhead;

    public DueDateAlertService(TaskRepository tasks, NotificationPublisher notifier,
            @Value("${app.notifications.due-alerts.days-ahead:1}") int daysAhead) {
        this.tasks = tasks;
        this.notifier = notifier;
        this.daysAhead = Math.max(daysAhead, 0);
    }

    /** Déclenchement manuel, réservé aux administrateurs ; le traitement planifié appelle directement la méthode interne. */
    @PreAuthorize("hasRole('ADMIN')")
    public int runNow() {
        return generateTaskAlerts(LocalDate.now());
    }

    /** Crée les alertes manquantes à la date donnée ; renvoie le nombre de notifications créées. */
    public int generateTaskAlerts(LocalDate today) {
        int created = 0;
        for (Task task : tasks.findDueForAlert(OPEN, today.plusDays(daysAhead))) {
            LocalDate due = task.getDueDate();
            boolean overdue = due.isBefore(today);
            String title = overdue ? "Tâche en retard" : "Échéance proche";
            String message = "La tâche « " + task.getTitle() + " » "
                    + (overdue ? "est en retard (échéance le " + due.format(DAY) + ")."
                            : due.equals(today) ? "arrive à échéance aujourd'hui."
                                    : "arrive à échéance le " + due.format(DAY) + ".");
            if (notifier.notifyOnce(task.getAssignee().getUser(), NotificationType.TASK_DUE, title, message, "/tasks",
                    "TASK_DUE:" + task.getId() + ":" + due)) {
                created++;
            }
        }
        return created;
    }
}
