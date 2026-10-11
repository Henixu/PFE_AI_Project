package com.soprahr.interne.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/db")
    public ResponseEntity<Map<String, String>> database() {
        try {
            String version = jdbcTemplate.queryForObject("SELECT version()", String.class);
            String database = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
            return ResponseEntity.ok(Map.of("status", "UP", "database", database, "version", version));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "error", e.getMessage()));
        }
    }
}
