package com.example.dbmigration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the self-contained database migration job.
 *
 * <p>This is a non-web Spring Boot application whose only job is to run Flyway
 * migrations and exit. Flyway is executed by the {@code FlywayMigrationStrategy}
 * bean (see {@link FlywayJobConfig}) during context startup, after which the JVM
 * has nothing left to keep it alive, so the process terminates with an exit code
 * that reflects success (0) or failure (non-zero). This makes it a clean CI/CD
 * migration step: {@code java -jar db-migration.jar --spring.profiles.active=prod}.
 */
@SpringBootApplication
public class DbMigrationApplication {

    public static void main(String[] args) {
        // SpringApplication.exit closes the context and computes the exit code;
        // System.exit propagates it so failed migrations fail the pipeline.
        System.exit(SpringApplication.exit(SpringApplication.run(DbMigrationApplication.class, args)));
    }
}
