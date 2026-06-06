// adapter-partner — external I/O: partner fulfilment (Resilience4j). Library bundled into the core
// deployable for now; becomes its own deployable on Day 5. Implements the fulfilment port from common.

val resilience4jVersion: String by project

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter")     // beans + config properties
    implementation("org.springframework.boot:spring-boot-starter-aop") // Resilience4j annotation aspects
    implementation("io.github.resilience4j:resilience4j-spring-boot3:$resilience4jVersion")
}
