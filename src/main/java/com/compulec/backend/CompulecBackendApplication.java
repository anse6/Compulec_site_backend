package com.compulec.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Point d'entrée principal de l'application Compulec Backend.
 * API REST construite avec Spring Boot 4, Spring Security, JWT et MySQL.
 *
 * @author Compulec Team
 * @version 1.0.0
 */
@SpringBootApplication
public class CompulecBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompulecBackendApplication.class, args);
    }
}
