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
    ├── application.yml                 # base config + layered Flyway locations
    ├── application-{local,dev,test,stg,prod}.yml   # each sets app.env
    └── db/migration/sqlserver/         # {vendor} layer
        ├── ddl/                        # ① COMMON schema — identical every env
        │   ├── V1__init_core_schema.sql            # versioned structure
        │   └── R__100_views.sql                    # repeatable structural objects
        ├── masterdata/common/          # ② COMMON reference data (all envs)
        │   └── R__500_reference_customer_status.sql # repeatable, idempotent MERGE
        └── env/{local,dev,test,stg,prod}/masterdata/   # ③/④ per-env data only
            ├── R__800_app_config.sql               # env-specific config (MERGE)
            └── R__810_demo_customers.sql           # local/dev only demo data
src/integrationTest/                    # separate source set — NOT run by `test`
└── java/com/example/dbmigration/
    └── FlywayMigrationIT.java          # Testcontainers: real SQL Server end-to-end
```

### How the layers combine

`spring.flyway.locations` stacks three folders, which Flyway merges into **one
version-ordered history**:

```
classpath:db/migration/{vendor}/ddl                       # common structure
classpath:db/migration/{vendor}/masterdata/common         # common reference data
classpath:db/migration/{vendor}/env/${app.env}/masterdata # THIS env's data only
```

`{vendor}` resolves to `sqlserver`; `${app.env}` is set by the active profile
(`local`/`dev`/`test`/`stg`/`prod`), so each environment loads exactly one data
overlay. **The `ddl` layer is identical in every environment — schema never
forks; only data differs.**

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

### Connection details from an external service (extension point)

Static `spring.datasource.*` is the default, but production credentials often
come from a secrets service (Vault, AWS/Azure secrets managers, a config
server). Publish **one** bean implementing `DbCredentialsProvider` and it takes
over — `ConnectionDetailsConfig` adapts it into the `JdbcConnectionDetails`
Spring Boot uses to build both the DataSource and Flyway:

```java
@Component
class VaultCredentialsProvider implements DbCredentialsProvider {
    private final VaultClient vault;
    VaultCredentialsProvider(VaultClient vault) { this.vault = vault; }
    @Override public DbCredentials get() {
        var s = vault.read("secret/data/appdb");
        return new DbCredentials(s.get("url"), s.get("username"), s.get("password"));
    }
}
```

- Resolved once at startup, before migrations run; throw to fail fast.
- With no such bean, connection details come from the profile files / env vars.
- It backs off when a `JdbcConnectionDetails` already exists, so it never
  interferes with Testcontainers' `@ServiceConnection` in tests.

A ready-to-adapt example, `ExternalServiceCredentialsProvider`, ships disabled;
enable it with `db.credentials.source=external-service` and point
`db.credentials.secrets-file` at a bundle, or replace its body with your client.

## Artifact versioning

The jar is versioned and self-describing so you always know which migration
bundle is deployed:

- **Version** is single-sourced from `gradle.properties` and overridable in CI:
  `./gradlew bootJar -Pversion=2.3.1` → `build/libs/db-migration-2.3.1.jar`.
- Each jar embeds `META-INF/build-info.properties` with `build.version`,
  `build.time`, `build.group/artifact`, and the git `build.commit`.
- On startup the job logs its own identity, e.g.
  `db-migration 2.3.1 (commit 2cfdd85) starting; active profiles: [prod]`,
  so deployment logs record exactly what ran.

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
./gradlew flywayInfo -PflywayEnv=dev
./gradlew flywayValidate -PflywayEnv=dev
```

## Writing migrations

### Where does my change go? (the four buckets)

| What you're changing | Folder | Kind | Rule |
|----------------------|--------|------|------|
| ① Schema (tables, indexes, constraints) | `ddl/` | `V__` versioned | Common to every env; schema never forks |
| Views / procedures / functions | `ddl/` | `R__1xx` repeatable | `CREATE OR ALTER`; runs after `V__` |
| ② Common reference/lookup data | `masterdata/common/` | `R__5xx` repeatable | **Idempotent MERGE** — same in every env |
| ④ Environment-specific data | `env/{env}/masterdata/` | `R__8xx` repeatable | **Idempotent MERGE** — that env only |
| One-time common data backfill | `masterdata/common/` | `V__` versioned | Runs once; keep it common, never per-env |

This split follows Flyway's own guidance — repeatable migrations are recommended
for "(re-)creating views/procedures" and "bulk reference data reinserts", and
environment-specific data should never pollute the shared versioned line.

### Rules

- **DDL is common and identical across all environments.** Per-environment
  schema differences are deliberately not supported — only *data* differs by env.
- **Repeatable data migrations must be idempotent.** They re-run whenever their
  checksum changes, so use `MERGE` (or `WHERE NOT EXISTS`), never a bare
  `INSERT`, or you'll get duplicates.
- **Ordering.** All `V__` run first in version order; then all `R__` in
  description order. The numeric prefixes enforce structure (`R__1xx`) →
  common data (`R__5xx`) → env data (`R__8xx`), so env data can rely on the
  reference data already being present.
- **Versioning.** Because schema is common-only, all `V__` form one clean line
  (`V1`, `V2`, …) with no cross-environment collisions to manage.
- Use `GO` to separate T-SQL batches.
- **Schema-qualify every object** (`app.customer`, not `customer`). SQL Server
  does not let Flyway set the session default schema, so unqualified objects
  would land in `dbo`. Flyway itself creates and owns the `app` schema and keeps
  its `flyway_schema_history` table there.
- Never edit a `V__` migration that has already been applied anywhere — add a
  new one. (Editing an `R__` is fine; that's the point — it re-applies.)

### Edition & rollback

This project uses the **free, open-source (Community) Flyway** only — no
Enterprise/paid features. Flyway's automatic `undo` command is Enterprise-only,
so rollback here follows the standard Community practice of **forward
compensation**: to reverse a versioned change, add a new higher-versioned
`V__revert_*.sql` with the inverse SQL rather than "undoing" the original. This
keeps the schema history append-only and auditable. Take a database
backup/snapshot before destructive changes.

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
the container, runs the real layered migrations, and asserts each bucket
materialised: common DDL, common reference data (`customer_status`), the local
env overlay (`app_config.env.name = local`), and the demo customers feeding the
repeatable view. If no Docker daemon is reachable, an `@EnabledIf` guard makes it
**skip gracefully** (the task still succeeds) instead of failing — so a
Docker-less machine or CI stage is never blocked by it.

## Extending to another database

The `classpath:db/migration/{vendor}` location means each database gets its own
script folder. To add, say, PostgreSQL:

1. `build.gradle.kts`: add `org.flywaydb:flyway-database-postgresql` and the
   `org.postgresql:postgresql` driver.
2. Create `src/main/resources/db/migration/postgresql/` mirroring the same
   layered layout (`ddl/`, `masterdata/common/`, `env/{env}/masterdata/`) with
   that dialect's scripts.
3. Point `DB_URL` / driver at PostgreSQL via a profile.

Flyway resolves `{vendor}` at runtime, so no code changes are needed.
