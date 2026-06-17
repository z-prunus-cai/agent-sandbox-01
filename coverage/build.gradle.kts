// Dedicated project that aggregates JaCoCo coverage across all JVM modules
// into a single report (build/reports/jacoco/testCodeCoverageReport/html).
// This is the Gradle-recommended approach over hand-rolled merge tasks.
plugins {
    base
    id("jacoco-report-aggregation")
}

dependencies {
    jacocoAggregation(project(":common"))
    jacocoAggregation(project(":backend"))
    jacocoAggregation(project(":batch"))
}

reporting {
    reports {
        val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
            testSuiteName = "test"
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("testCodeCoverageReport"))
}
