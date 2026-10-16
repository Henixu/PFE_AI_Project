package com.soprahr.interne.service;

import org.springframework.stereotype.Service;

import com.soprahr.interne.entity.AuditLog;
import com.soprahr.interne.repository.AuditLogRepository;
import com.soprahr.interne.repository.UserRepository;

/**
 * Journal d'audit. S'exécute dans la transaction de l'opération auditée : si celle-ci échoue,
 * la trace est annulée avec elle. Ne jamais y mettre de mot de passe ni de jeton.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    public void record(String actorEmail, String action, String entityType, Long entityId, String details) {
        AuditLog log = new AuditLog();
        userRepository.findByEmailIgnoreCase(actorEmail).ifPresent(log::setUser);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}
