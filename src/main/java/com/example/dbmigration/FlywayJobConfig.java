package com.example.dbmigration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Drives Flyway according to {@link MigrationProperties.Mode}.
 *
 * <p>Defining a {@link FlywayMigrationStrategy} bean overrides Spring Boot's
 * default "always migrate on startup" behaviour, so the job can also validate,
 * report, repair, or baseline without running migrations — chosen per environment.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MigrationProperties.class)
public class FlywayJobConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayJobConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(MigrationProperties properties) {
        return flyway -> {
            log.info("Running Flyway in '{}' mode against default schema '{}'",
                    properties.getMode(), flyway.getConfiguration().getDefaultSchema());
            switch (properties.getMode()) {
                case MIGRATE -> migrate(flyway);
                case VALIDATE -> {
                    flyway.validate();
                    log.info("Validation succeeded: applied migrations match the scripts on the classpath.");
                }
                case INFO -> info(flyway);
                case REPAIR -> {
                    flyway.repair();
                    log.info("Schema history repair complete.");
                }
                case BASELINE -> {
                    flyway.baseline();
                    log.info("Baseline complete at version {}.", flyway.getConfiguration().getBaselineVersion());
                }
            }
        };
    }

    private static void migrate(Flyway flyway) {
        MigrateResult result = flyway.migrate();
        log.info("Applied {} migration(s); schema '{}' is now at version {}.",
                result.migrationsExecuted, result.schemaName, result.targetSchemaVersion);
    }

    private static void info(Flyway flyway) {
        log.info("Migration status:");
        for (MigrationInfo mi : flyway.info().all()) {
            log.info("  {} | {} | {} | {}",
                    mi.getVersion() != null ? mi.getVersion() : "(repeatable)",
                    mi.getState(),
                    mi.getType(),
                    mi.getDescription());
        }
    }
}
