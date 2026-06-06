// domain-core — the domain layer, shipped as a plain library ("DLL"): no main(), not bootable.
// Owns data (members, tiers, append-only ledger), the outbox table, domain invariants, and the
// outbox relay. It is bundled into the business-api ("core") deployable and its beans run inside
// that process. Java toolchain, the Spring BOM, and JUnit wiring come from the root subprojects block.

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")        // balance read controller
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.kafka:spring-kafka")                  // outbox relay (publish)
}
