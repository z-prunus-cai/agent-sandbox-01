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

        // Only common DDL is versioned; the single V1 is the current version.
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
    }

    @Test
    void commonReferenceDataIsSeededInEveryEnvironment() throws Exception {
        // Bucket ②: common reference data (R__5xx) — present regardless of env.
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM app.customer_status")) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(3); // ACTIVE, INACTIVE, SUSPENDED
        }
    }

    @Test
    void environmentSpecificDataMatchesActiveEnvironment() throws Exception {
        // No profile is activated, so the default profile "local" is used and the
        // env/local overlay (R__8xx) is applied — bucket ④.
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT config_value FROM app.app_config WHERE config_key = 'env.name'")) {
            rs.next();
            assertThat(rs.getString(1)).isEqualTo("local");
        }
    }

    @Test
    void localDemoCustomersFeedTheRepeatableView() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            // local demo data (R__810) seeds 3 customers, 2 of them ACTIVE.
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM app.customer")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(3);
            }
            // Bucket ① repeatable view exposes only the ACTIVE ones.
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM app.vw_active_customer")) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(2);
            }
        }
    }
}
