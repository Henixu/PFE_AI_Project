package com.soprahr.interne.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.soprahr.interne.dto.DueAlertsResponse;
import com.soprahr.interne.service.DueDateAlertService;

/** Route d'administration : /api/admin/** est réservé au rôle ADMIN (SecurityConfig, doublé par @PreAuthorize). */
@RestController
@RequestMapping("/api/admin/notifications")
public class AdminNotificationController {

    private final DueDateAlertService alerts;

    public AdminNotificationController(DueDateAlertService alerts) {
        this.alerts = alerts;
    }

    /** Exécute immédiatement la génération des alertes d'échéance (utile pour vérifier la règle sans attendre l'heure planifiée). */
    @PostMapping("/due-alerts/run")
    public DueAlertsResponse runDueAlerts() {
        return new DueAlertsResponse(alerts.runNow());
    }
}
