package com.soprahr.interne.service;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Lance chaque jour la génération des alertes d'échéance (heure réglable, désactivable par configuration). */
@Component
@ConditionalOnProperty(name = "app.notifications.due-alerts.enabled", havingValue = "true", matchIfMissing = true)
public class DueDateAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(DueDateAlertScheduler.class);

    private final DueDateAlertService alerts;

    public DueDateAlertScheduler(DueDateAlertService alerts) {
        this.alerts = alerts;
    }

    @Scheduled(cron = "${app.notifications.due-alerts.cron:0 0 7 * * *}")
    public void run() {
        try {
            int created = alerts.generateTaskAlerts(LocalDate.now());
            log.info("Alertes d'échéance : {} notification(s) créée(s)", created);
        } catch (RuntimeException e) {
            // Une erreur ne doit pas arrêter la planification : le traitement du lendemain réessaiera.
            log.error("Échec de la génération des alertes d'échéance", e);
        }
    }
}
