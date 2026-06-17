plugins {
    id("buildlogic.spring-app-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.batch)
    runtimeOnly("com.h2database:h2")
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.batch.test)
}
