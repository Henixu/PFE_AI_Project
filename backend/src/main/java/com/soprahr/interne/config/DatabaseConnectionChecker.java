package com.soprahr.interne.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Vérifie au démarrage que la connexion PostgreSQL est opérationnelle.
 */
@Component
public class DatabaseConnectionChecker {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionChecker.class);

    private final DataSource dataSource;

    public DatabaseConnectionChecker(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkConnection() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            log.info("Connexion PostgreSQL établie : {} {} ({})",
                    meta.getDatabaseProductName(), meta.getDatabaseProductVersion(), meta.getURL());
        } catch (Exception e) {
            log.error("Impossible de se connecter à PostgreSQL : {}", e.getMessage());
        }
    }
}
