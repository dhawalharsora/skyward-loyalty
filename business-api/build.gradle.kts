plugins {
    java
    // The bootable "core" deployable (business + domain). Produces the runnable bootJar.
    id("org.springframework.boot")
}

val springdocVersion: String by project
val resilience4jVersion: String by project

dependencies {
    implementation(project(":domain-core"))
    implementation(project(":adapter-partner"))
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springdocVersion")

    // The boot app is the runtime that executes Flyway migrations and talks to Postgres.
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Integration tests run against real Postgres + Kafka via Testcontainers.
    // For tests that reset the circuit breaker between cases (runtime dep comes via adapter-partner).
    testImplementation("io.github.resilience4j:resilience4j-spring-boot3:$resilience4jVersion")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}
