package com.compulec.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Ajustements de schéma que "ddl-auto=update" ne fait pas (il n'agrandit jamais une colonne existante).
 * Idempotent : sans effet si la colonne a déjà la bonne taille. MySQL uniquement.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class SchemaUpgrade implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql")) {
                return;
            }
        } catch (Exception e) {
            log.warn("Mise à jour du schéma ignorée : {}", e.getMessage());
            return;
        }
        widen("projets", "service", 500, true);       // plusieurs services par projet
        widen("projets", "localisation", 255, false); // adresse choisie sur la carte
    }

    private void widen(String table, String column, int length, boolean nullable) {
        try {
            Integer current = jdbcTemplate.queryForObject(
                    "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            if (current != null && current < length) {
                jdbcTemplate.execute("ALTER TABLE " + table + " MODIFY " + column + " VARCHAR(" + length + ")"
                        + (nullable ? " NULL" : " NOT NULL"));
                log.info("Colonne {}.{} agrandie : {} → {} caractères", table, column, current, length);
            }
        } catch (Exception e) {
            log.warn("Impossible d'agrandir {}.{} : {}", table, column, e.getMessage());
        }
    }
}
