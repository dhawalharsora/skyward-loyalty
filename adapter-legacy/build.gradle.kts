// adapter-legacy — the legacy SOAP tier service (Spring-WS), representing the old SOA being strangled.
// Standalone bootable deployable with an in-memory store (its own "system of record", no DB), so it can
// legitimately disagree with the new domain service — which is what makes shadow-compare meaningful.
// Config inherited from the root `subprojects {}` block.

plugins {
    java
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))

    // Spring-WS (contract-first SOAP) + an embedded servlet container to host the dispatcher servlet.
    implementation("org.springframework.boot:spring-boot-starter-web-services")
    // wsdl4j lets DefaultWsdl11Definition publish a real WSDL generated from the XSD contract.
    implementation("wsdl4j:wsdl4j")
    // Actuator: health/readiness probes (consumed by the demo health-wait and by k8s).
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // spring-ws-test: MockWebServiceClient drives the endpoint with real SOAP payloads (no socket).
    testImplementation("org.springframework.ws:spring-ws-test")
}
