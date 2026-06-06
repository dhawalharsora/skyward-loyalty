// common — shared events, DTOs (records), idempotency utils. Plain library, no Spring web.
// Java toolchain, BOM, and test wiring come from the root `subprojects {}` block.

dependencies {
    // Validation annotations may be shared by DTOs across modules.
    implementation("org.springframework.boot:spring-boot-starter-validation")
}
