package com.example.dbmigration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the migration job, bound from {@code db.migration.*}.
 *
 * <p>The {@link Mode} lets the same artifact perform different Flyway actions
 * depending on the pipeline stage, selected via {@code db.migration.mode} or the
 * {@code DB_MIGRATION_MODE} environment variable.
 */
@ConfigurationProperties(prefix = "db.migration")
public class MigrationProperties {

    /** What the job should do when it starts. */
    public enum Mode {
        /** Apply all pending migrations (default). */
        MIGRATE,
        /** Verify applied migrations match the scripts on the classpath; make no changes. */
        VALIDATE,
        /** Print the migration status table; make no changes. */
        INFO,
        /** Repair the schema history table (e.g. after a failed migration). */
        REPAIR,
        /** Baseline an existing, non-empty database at the configured baseline version. */
        BASELINE
    }

    private Mode mode = Mode.MIGRATE;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }
}
