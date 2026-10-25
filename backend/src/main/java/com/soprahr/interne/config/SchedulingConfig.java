package com.soprahr.interne.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Active les traitements planifiés (@Scheduled), ex. les alertes d'échéance. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
