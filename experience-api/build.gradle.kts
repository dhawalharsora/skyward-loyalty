// experience-api — the BFF / edge, now bootable. Hosts the strangler routing facade for
// GET /members/{id}/tier: a config-driven, sticky-by-member split between the legacy SOAP service and
// the new domain REST service. No business rules, no data ownership — it only routes and normalizes.
// Config inherited from the root `subprojects {}` block.

plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-web")          // REST endpoint + RestClient
    implementation("org.springframework.boot:spring-boot-starter-web-services") // WebServiceTemplate (SOAP client)
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Client-side mock servers: MockRestServiceServer (in spring-test) for the REST path and
    // MockWebServiceServer (spring-ws-test) for the SOAP path — no real backends needed.
    testImplementation("org.springframework.ws:spring-ws-test")
}
