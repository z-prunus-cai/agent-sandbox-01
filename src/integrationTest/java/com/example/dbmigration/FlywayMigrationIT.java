package com.example.dbmigration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

/**
 * End-to-end migration test against a real SQL Server in a container.
 *
 * <p>{@code @ServiceConnection} wires the Boot datasource to the container, so
 * when the application context starts the {@code FlywayMigrationStrategy} runs
 * the real migrations against real SQL Server. The assertions then confirm the
 * schema, seed data and repeatable view all materialised.
 *
 * <p>This lives in the {@code integrationTest} source set, so it never runs in
 * the default {@code ./gradlew test}. It runs only via {@code ./gradlew
 * integrationTest}, and the {@code @EnabledIf} guard makes it self-skip (rather
 * than fail) when no Docker daemon is available. It pulls the SQL Server image
 * on first run, so it may take a minute the first time.
 */
@SpringBootTest
@Testcontainers
@EnabledIf("dockerAvailable")
class FlywayMigrationIT {

    /** Skip the whole class when there is no reachable Docker daemon. */
    static boolean dockerAvailable() {
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @Container
    @ServiceConnection
    static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense();

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationsApplyCleanlyAndValidate() {
        // If applied migrations diverged from the scripts, validate() throws.
        flyway.validate();

        // V1 + V2 are versioned; the state should be at version 2.
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
    }

    @Test
    void seedDataAndRepeatableViewArePresent() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {

            // Seed rows from V2.
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM app.customer")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(3);
            }

            // Repeatable view R__ should only expose ACTIVE customers.
            try (ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) FROM app.vw_active_customer")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }
}
