rootProject.name = "skyward-loyalty"

// Four-layer architecture mapped onto Gradle modules.
// Package boundaries stay clean even though we ship 3 deployables (see README).
include(
    "common",          // shared events, DTOs (records), idempotency utils — no Spring web
    "domain-core",     // owns data: members, tiers, append-only ledger, outbox + relay
    "business-api",    // orchestration & rules: earning rules, redemption saga
    "experience-api",  // BFF / edge: strangler routing facade
    "adapter-partner", // external I/O: partner accrual ingest + fulfilment (Resilience4j)
    "adapter-legacy",  // external I/O: legacy SOAP tier service + client
)
