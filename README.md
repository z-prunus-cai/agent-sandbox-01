# db-migration

A **self-contained database change (schema migration) project** built on
**Spring Boot 4.1** + **Flyway 12.4**, targeting **Microsoft SQL Server** and
designed to be extended to other databases.

The same versioned SQL scripts can be run two ways:

1. **As a Spring Boot "migration job"** — reads YAML / profiles / environment
   variables, applies migrations, then exits with a success/failure exit code.
   This is the CI/CD entry point.
2. **Via the Flyway Gradle plugin** — `flywayInfo` / `flywayValidate` for quick
   local inspection without booting the app.

## Requirements

| Tool | Version |
|------|---------|
| JDK  | 21 (LTS) |
| Gradle | Wrapper included (`./gradlew`) |
| SQL Server | 2017+ (tested against `mcr.microsoft.com/mssql/server:2022-latest`) |
| Docker | Optional — only for `./gradlew integrationTest` (auto-skipped if absent) |

Versions are pinned by the Spring Boot 4.1.0 BOM: Flyway `12.4.0`,
mssql-jdbc `13.4.0.jre11`, Testcontainers `2.0.5`.

## Project layout

```
build.gradle.kts                       # Boot + Flyway plugins, deps, Gradle Flyway config
settings.gradle.kts
src/main/
├── java/com/example/dbmigration/
│   ├── DbMigrationApplication.java     # Boot entry point (migrate then exit)
│   ├── FlywayJobConfig.java            # FlywayMigrationStrategy: migrate|validate|info|repair|baseline
│   └── MigrationProperties.java        # binds db.migration.mode
└── resources/
    ├── application.yml                 # base config (no secrets)
    ├── application-{local,dev,test,stg,prod}.yml
    └── db/migration/sqlserver/         # {vendor} layer — SQL Server T-SQL scripts
        ├── V1__init_customer_schema.sql
        ├── V2__seed_reference_data.sql
        └── R__vw_active_customer.sql   # repeatable (views/procs/functions)
src/integrationTest/                    # separate source set — NOT run by `test`
└── java/com/example/dbmigration/
    └── FlywayMigrationIT.java          # Testcontainers: real SQL Server end-to-end
```

## Configuration

Connection details are **never** stored in the repo. Every environment reads
them from environment variables:

| Variable | Meaning | Example |
|----------|---------|---------|
| `DB_URL` | JDBC URL | `jdbc:sqlserver://db:1433;databaseName=appdb;encrypt=true` |
| `DB_USER` | Login | `migrator` |
| `DB_PASSWORD` | Password | *(from secret store)* |
| `DB_MIGRATION_MODE` | `migrate` (default) `\| validate \| info \| repair \| baseline` | `validate` |
| `SPRING_PROFILES_ACTIVE` | `local \| dev \| test \| stg \| prod` | `prod` |

Profile differences (safety hardening as you move toward prod):

| Profile | `clean` allowed | out-of-order | auto-baseline |
|---------|-----------------|--------------|---------------|
| local   | yes | no  | — |
| dev     | yes | yes | — |
| test    | no  | no  | — |
| stg     | no  | no  | no |
| prod    | no  | no  | no |

## Running

### As a Boot migration job

```bash
# Local (uses application-local.yml defaults; override with env vars)
./gradlew bootRun

# Any environment, from the packaged jar (the CI/CD pattern):
./gradlew bootJar
DB_URL='jdbc:sqlserver://db:1433;databaseName=appdb;encrypt=true;trustServerCertificate=true' \
DB_USER=migrator DB_PASSWORD=*** \
java -jar build/libs/db-migration-1.0.0.jar --spring.profiles.active=prod

# Validate only (no changes):
DB_MIGRATION_MODE=validate java -jar build/libs/db-migration-1.0.0.jar --spring.profiles.active=prod
```

The process exits `0` on success and non-zero if a migration fails, so it plugs
directly into a pipeline step.

### Via the Flyway Gradle plugin (dev convenience)

```bash
export DB_URL='jdbc:sqlserver://localhost:1433;databaseName=appdb;encrypt=true;trustServerCertificate=true'
export DB_USER=sa DB_PASSWORD='Local_Str0ng_Passw0rd'
./gradlew flywayInfo
./gradlew flywayValidate
```

## Writing migrations

- **Versioned**: `V<n>__<description>.sql` — applied once, in order.
- **Repeatable**: `R__<description>.sql` — re-applied whenever its checksum
  changes (use for views / stored procedures / functions).
- Use `GO` to separate T-SQL batches.
- **Schema-qualify every object** (`app.customer`, not `customer`). SQL Server
  does not let Flyway set the session default schema, so unqualified objects
  would land in `dbo`. Flyway itself creates and owns the `app` schema and keeps
  its `flyway_schema_history` table there.
- Never edit a migration that has already been applied anywhere — add a new one.

## Testing

Tests are split by whether they need external infrastructure:

```bash
# Fast unit tests — NO Docker required. This is the default verification path
# and the only one wired into `check` / `build`.
./gradlew test

# Integration tests — start a real SQL Server container and run the actual
# migrations against it. Requires Docker; run this only in a Docker environment.
./gradlew integrationTest
```

`FlywayMigrationIT` lives in the `integrationTest` source set, so it never runs
during `./gradlew test`. It uses `@ServiceConnection` to point the datasource at
the container and asserts the schema, seed data and repeatable view all
materialised. If no Docker daemon is reachable, an `@EnabledIf` guard makes it
**skip gracefully** (the task still succeeds) instead of failing — so a
Docker-less machine or CI stage is never blocked by it.

## Extending to another database

The `classpath:db/migration/{vendor}` location means each database gets its own
script folder. To add, say, PostgreSQL:

1. `build.gradle.kts`: add `org.flywaydb:flyway-database-postgresql` and the
   `org.postgresql:postgresql` driver.
2. Create `src/main/resources/db/migration/postgresql/` with that dialect's
   scripts.
3. Point `DB_URL` / driver at PostgreSQL via a profile.

Flyway resolves `{vendor}` at runtime, so no code changes are needed.
