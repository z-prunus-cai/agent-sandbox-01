# agent-sandbox-01

Sample Java + Gradle **monorepo** demonstrating current best practices for a
four-module project:

| Module     | Type                | Purpose                                                   |
|------------|---------------------|-----------------------------------------------------------|
| `common`   | `java-library`      | Reusable code shared by `backend` and `batch`.            |
| `backend`  | Spring Boot **Web** | REST API **and** host for the frontend's static assets.   |
| `batch`    | Spring **Batch**    | Batch job reusing `common`.                               |
| `frontend` | React + Vite + TS   | SPA, built and tested via Gradle, bundled into `backend`. |
| `coverage` | aggregation         | Single cross-module JaCoCo coverage report.               |

Toolchain: Java 21, Gradle 8.14.3, Spring Boot 3.5.x, Node 22 (auto-provisioned),
Vite 6 + Vitest.

## Key design decisions (the "best practices")

### 1. Convention plugins in an included `build-logic` build
Shared build configuration (Java toolchain, test framework, JaCoCo) lives in
[`build-logic/`](build-logic) as **precompiled script plugins** and is wired in
via `includeBuild("build-logic")` in [`settings.gradle.kts`](settings.gradle.kts).
This is the modern replacement for `buildSrc` / copy-pasted `subprojects { }`
blocks: faster, cacheable, and explicit. Modules stay tiny:

```kotlin
plugins { id("buildlogic.java-common-conventions") }      // common
plugins { id("buildlogic.spring-app-conventions") }       // backend, batch
```

### 2. Single source of versions — version catalog
All dependency and plugin versions live in
[`gradle/libs.versions.toml`](gradle/libs.versions.toml) and are reused even
inside `build-logic`.

### 3. Frontend build output → backend static resources
The genuinely tricky part. The flow is **variant-aware artifact sharing** (no
reaching into another project's tasks, so project isolation/config caching stay
intact):

1. [`frontend/build.gradle.kts`](frontend/build.gradle.kts) uses the
   [node-gradle plugin](https://github.com/node-gradle/gradle-node-plugin) to
   provision Node, run `vite build` (output -> `frontend/build/dist`), and
   exposes that directory through a **consumable** configuration
   `frontendAssets`.
2. [`backend/build.gradle.kts`](backend/build.gradle.kts) declares a matching
   **resolvable** configuration and makes `processResources` copy the resolved
   files into `static/`. Spring Boot then serves the SPA from the root context,
   and it ends up inside the bootJar at `BOOT-INF/classes/static/`.

Gradle wires the `frontendBuild` -> `processResources` task dependency
automatically from the artifact, so `./gradlew :backend:bootJar` builds the
frontend first.

Use `-Pskip.frontend=true` for backend-only iterations (skips the npm build).

### 4. Test coverage everywhere
- **JVM modules**: the `buildlogic.java-common-conventions` plugin applies
  JaCoCo, generates XML+HTML reports, and enforces a **50% line-coverage floor**
  via `jacocoTestCoverageVerification` wired into `check`. Bootstrap/config
  classes (`*Application`, `**/config/**`) are excluded.
- **Aggregation**: the [`coverage`](coverage) project uses Gradle's official
  `jacoco-report-aggregation` plugin to merge all modules into one report.
- **Frontend**: Vitest runs with V8 coverage and its own 50% thresholds; the
  `frontendTest` task is wired into Gradle's `check`.

> Note: the Spring Boot BOM is imported as a **native Gradle platform** (not the
> `io.spring.dependency-management` plugin) specifically so managed versions
> propagate transitively to the `coverage` aggregation classpath.

## Common commands

```bash
./gradlew build                 # compile, test, enforce coverage, build bootJar (frontend bundled)
./gradlew check                 # all tests + coverage verification (JVM + frontend)
./gradlew testCodeCoverageReport -p coverage   # aggregated coverage report
./gradlew :backend:bootRun      # run the web app at http://localhost:8080
./gradlew :batch:bootRun        # run the batch job
./gradlew build -Pskip.frontend=true           # skip the npm build
```

Reports:
- Aggregated coverage: `coverage/build/reports/jacoco/testCodeCoverageReport/html/index.html`
- Per-module coverage: `<module>/build/reports/jacoco/test/html/index.html`
- Frontend coverage: `frontend/build/coverage/index.html`
