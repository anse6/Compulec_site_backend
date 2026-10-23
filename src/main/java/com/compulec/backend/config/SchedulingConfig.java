package com.compulec.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active les tâches planifiées (maintenance du chat, signal de vie des flux temps réel)
 * et les traitements en arrière-plan (e-mails d'alerte du chat).
 */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
