# Skyward Loyalty — Code Walkthrough

> A single document to understand the system: **what it is**, **what each piece of code does**, **why it's
> built that way**, and the **Java / Spring / library nuances** worth knowing. Code snippets are trimmed from
> the real source; file paths are given so you can jump in.

---

## 1. What this is, in one breath

An event-driven **loyalty points integration platform** ("Skyward"). It demonstrates the
engineering an airline loyalty/integration team actually does: layered microservices, REST + Kafka, and
incremental modernisation of a legacy SOAP system. Three patterns are the substance:

| Pattern | Where | The problem it solves |
|---|---|---|
| **Transactional Outbox** | Accrual flow | The *dual-write* problem — never lose/duplicate an event vs. the DB write. |
| **Orchestrated Saga + compensation** | Redemption flow | A multi-step business transaction across a flaky partner, with no distributed transaction. |
| **Strangler Fig** | Tier-read flow | Migrate a legacy SOAP service to a new microservice, incrementally and reversibly. |

Everything else (append-only ledger, idempotency, projections, DLQ, Resilience4j) exists to make those
three correct under failure.

---

## 2. The mental model (read this before the code)

- **Four logical layers, dependencies point inward to the domain:**
  - **Experience** (`experience-api`) — thin edge/BFF. The strangler routing facade. No data, no rules.
  - **Business** (`business-api`) — orchestration & rules. Earning rules, redemption saga. Owns *process*.
  - **Domain** (`domain-core`) — owns *data*: members, tiers, the **append-only ledger**, the **outbox**,
    the balance **projection**. Domain has no Kafka, no external I/O.
  - **Adapter** (`adapter-partner`, `adapter-legacy`) — all external I/O: partner fulfilment (Resilience4j)
    and the legacy SOAP service.
- **Three deployables** (four layers, packaged into three runnable services to stay achievable):

| Deployable | Port | Contains |
|---|---|---|
| `experience-api` | 8082 | Experience (strangler facade) |
| `business-api` "core" | 8080 | Business + Domain + the partner adapter, in one process |
| `adapter-legacy` | 8081 | The legacy SOAP service (the "old SOA") |

- **Balance is never a column you overwrite.** It is *derived*: the **ledger** (append-only earn/burn rows)
  is the source of truth; `member_balance` is a **materialized projection** kept up to date by a Kafka
  consumer. Think event-sourcing-lite for the part that matters (money/points).

**Data model (PostgreSQL, all via Flyway migrations):**

| Table | Role |
|---|---|
| `member` | Members + their `tier` (mutable aggregate). |
| `ledger_entry` | **Append-only** earn/burn rows. Source of truth for balance. |
| `outbox_event` | Events to publish, written in the same tx as the business change. |
| `member_balance` | Materialized balance projection (O(1) read). |
| `processed_event` | **Inbox** — event ids already applied, for idempotent consumers. |
| `redemption` | Saga state machine rows. |

---

## 3. Flow 1 — Accrual via Transactional Outbox

**Story:** a partner reports a member earned points → we apply tier multiplier + campaign bonus → in **one
DB transaction** we write the ledger entry *and* an outbox row → a relay publishes the event to Kafka → a
consumer updates the balance projection.

### 3a. The atomic write (the heart of the pattern)
`business-api/.../accrual/AccrualService.java`

```java
@Transactional
public AccrualResponse accrue(AccrualRequest request) {
    // Fast-path idempotency: replayed key → return original result.
    Optional<LedgerEntry> existing = ledger.findByIdempotencyKey(request.idempotencyKey());
    if (existing.isPresent()) { /* return DUPLICATE */ }

    Member member = members.findById(request.memberId()).orElseThrow(...);
    long earned = earningRules.earnedPointsFor(member, request.basePoints(), request.source());

    // 1) business change: append-only ledger entry
    ledger.save(LedgerEntry.earn(member.getId(), earned, request.source(), request.idempotencyKey()));

    // 2) event to publish — SAME transaction, so it commits atomically with (1)
    PointsAccrued event = new PointsAccrued(UUID.randomUUID(), member.getId(), earned, ...);
    outbox.save(OutboxEvent.pointsAccrued(member.getId(), toJson(event)));

    return new AccrualResponse(AccrualStatus.ACCRUED, member.getId(), earned, entry.getId());
}
```

**Why:** if you instead did `db.commit()` then `kafka.send()`, a crash between them loses the event (or a
send-then-commit loses the data). One transaction over *both rows* removes that window. The event is just
another row until the relay ships it.

> **The shortcut, named:** idempotency is a fast-path read + a `UNIQUE` constraint on the ledger's
> idempotency key as backstop. A truly concurrent double-submit makes the second tx fail the constraint
> (correct — no double credit), rather than gracefully returning `DUPLICATE`. That graceful race handling
> is a noted future refinement.

### 3b. The relay (crash-safety lives here)
`domain-core/.../outbox/relay/OutboxRelay.java` + `OutboxEventRepository.java`

```java
// The poll query — note FOR UPDATE SKIP LOCKED (a native query; JPQL can't express it):
@Query(value = """
    SELECT * FROM outbox_event
    WHERE published_at IS NULL
    ORDER BY created_at
    FOR UPDATE SKIP LOCKED
    LIMIT :batchSize
    """, nativeQuery = true)
List<OutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);
```

```java
@Scheduled(fixedDelayString = "${skyward.outbox.relay.poll-interval-ms:1000}")
@Transactional
public void publishPending() {
    for (OutboxEvent event : outbox.lockUnpublishedBatch(batchSize)) {
        publish(event);                                  // synchronous: waits for broker ack (.get())
        event.markPublished(OffsetDateTime.now(UTC));    // dirty-checking → UPDATE flushed at commit
    }
}
```

**Three things worth understanding:**
1. **`FOR UPDATE SKIP LOCKED`** lets you run *multiple relay instances*: each grabs a disjoint set of rows
   (locked rows are skipped, not waited on), so no event is published twice across instances — and one slow
   row doesn't block the others.
2. **Publish *before* marking published.** If the process dies after the Kafka ack but before commit, the
   row stays `published_at IS NULL` and is re-sent next tick. That makes delivery **at-least-once** → so
   **consumers must be idempotent**. (Marking first would risk *losing* events on a crash — worse for points.)
3. **Production alternative:** log-based CDC (Debezium tailing the WAL) instead of polling — captured in
   ADR-0001. Polling is the simple, dependency-free version.

### 3c. The idempotent consumer + projection
`business-api/.../balance/PointsAccruedConsumer.java` → `domain-core/.../balance/BalanceProjectionService.java`

```java
@KafkaListener(topics = Topics.POINTS_ACCRUED)
public void onPointsAccrued(String payload) {
    PointsAccrued event = parse(payload);                       // unparseable → throw → retried → DLT
    projection.applyAccrued(event.eventId(), event.memberId(), event.points());
}
```

```java
@Transactional
private void applyDelta(UUID eventId, UUID memberId, long delta) {
    if (processedEvents.existsById(eventId)) return;            // at-least-once dedupe (the "inbox")
    processedEvents.save(ProcessedEvent.of(eventId));
    MemberBalance balance = balances.findById(memberId).orElseGet(() -> MemberBalance.zero(memberId));
    balance.add(delta);
    balances.save(balance);
}
```

The **inbox** (`processed_event`) is how the consumer survives the relay's at-least-once redelivery: record
the event id and update the balance in one tx; a redelivered event id is seen and skipped.

### 3d. Earning rules (a small but real "financial math" detail)
`business-api/.../accrual/EarningRules.java`

```java
long scaled = BigDecimal.valueOf(basePoints)
        .multiply(BigDecimal.valueOf(multiplier))   // combine tier × campaign multipliers FIRST
        .setScale(0, RoundingMode.HALF_UP)           // round ONCE at the end
        .longValueExact();
return scaled + bonus;                               // then add flat campaign bonus
```

Rounding once (not per step) avoids compounding rounding error. Campaigns are config-driven
(`skyward.earning.campaigns`), matched by `source` prefix — no redeploy to change a promo.

---

## 4. Flow 2 — Redemption as an orchestrated Saga

**Story:** a member redeems points → **reserve** a hold → **fulfil** with the partner (resilient call) →
**commit** (burn points) on success, or **compensate** (release hold) on a *definite* failure. Each step is
its own local transaction; the saga survives restarts.

### 4a. The orchestrator (coordinates; not itself transactional)
`business-api/.../redemption/RedemptionOrchestrator.java`

```java
public Redemption fulfilReserved(Redemption redemption) {
    try {
        FulfilmentResult result = partnerClient.fulfil(new FulfilmentRequest(...));
        redemptionService.markFulfilled(redemption.getId(), result.partnerReference());
        return redemptionService.commit(redemption.getId());          // burn points
    } catch (FulfilmentException e) {
        if (e.indeterminate()) {                                       // timeout / open circuit
            return redemption;                                         // leave RESERVED — DO NOT compensate
        }
        return redemptionService.compensate(redemption.getId(), e.getMessage());  // definite → release hold
    }
}
```

**The edge case not to hand-wave:** if the partner call **times out but the partner
actually fulfilled**, blindly compensating gives the reward *and* keeps the points. Two defences:
- **Definite vs indeterminate failure.** Only an explicit partner *rejection* is definite (safe to
  compensate). A timeout / open circuit / connection error is **indeterminate** → leave the saga `RESERVED`
  (hold preserved) for the recovery scheduler.
- **Idempotent re-fulfil keyed by `redemptionId`** → retry/recovery completes it exactly once.

### 4b. Why the steps are a *separate bean* (a Spring AOP trap you must know)
`business-api/.../redemption/RedemptionService.java` — each step is `@Transactional`.

The orchestrator (no `@Transactional`) calls `redemptionService.reserve/markFulfilled/commit/compensate`,
each a **separate** `@Transactional` method on a **different bean**. This is deliberate:

> **Nuance — `@Transactional` (and Resilience4j, and `@Async`) work via a proxy.** Spring wraps the bean in
> a proxy that opens/commits the transaction *around* the call. A method calling **its own** annotated
> method (`this.commit()`) bypasses the proxy — the annotation is silently ignored. So cross-cutting steps
> live on a separate bean and are invoked *through* it. (.NET analogue: think of an interceptor/decorator
> that only fires when the call crosses the proxy boundary.)

### 4c. Reservation = strongly consistent, no double-spend
```java
@Transactional
public Redemption reserve(UUID memberId, String rewardCode, long points, String idempotencyKey) {
    if (redemptions.findByIdempotencyKey(idempotencyKey).isPresent()) return existing;  // idempotent
    members.findAndLockById(memberId).orElseThrow(...);     // SELECT ... FOR UPDATE (per-member mutex)
    long available = ledger.balanceOf(memberId)             // ledger = synchronous source of truth
                   - redemptions.sumPointsByMemberIdAndStatusIn(memberId, ACTIVE_HOLDS);  // minus holds
    return redemptions.save(available >= points ? Redemption.reserved(...) : Redemption.failed(...));
}
```

**Why reserve against the ledger, not `member_balance`?** The projection is *eventually consistent* — fine
for **display**, wrong for a **decision**. Availability is computed from the ledger (truth) minus in-flight
holds, under a **per-member pessimistic lock** (`@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`) so two
concurrent redemptions can't spend the same points. The hold is a *status* (`RESERVED`/`FULFILLED`), not a
balance mutation; **commit** writes the BURN (and balance drops via the same outbox pipeline as accrual);
**compensate** just flips status — auditable, reversible.

### 4d. The resilient partner call (Resilience4j nuances)
`adapter-partner/.../PartnerFulfilmentGateway.java` + `ResilientPartnerFulfilmentClient.java`

```java
@Retry(name = INSTANCE)
@CircuitBreaker(name = INSTANCE)
@TimeLimiter(name = INSTANCE)                                  // requires an async return type
public CompletableFuture<FulfilmentResult> fulfil(FulfilmentRequest request) {
    return CompletableFuture.supplyAsync(() -> partner.invoke(request), executor);  // dedicated pool
}
```

Things to know:
- **Stacking order (outer→inner): `Retry → CircuitBreaker → TimeLimiter`.** Each retry passes through the
  breaker and is time-bounded; a timeout counts as a failure toward the breaker. Configured in
  `application.yml` under `resilience4j.*` (window size, failure-rate threshold, wait duration, timeout).
- **`@TimeLimiter` needs `CompletableFuture`** — that's why the method is async.
- **Dedicated bounded executor**, not `ForkJoinPool.commonPool`: the TimeLimiter *cancels* the future on
  timeout but **cannot interrupt a blocking call**, so a slow call keeps its thread until it returns; an
  isolated pool stops that from starving everything else.
- The client **joins** the future and normalises every failure into one `FulfilmentException` carrying the
  `indeterminate` flag (only `PartnerRejectedException` ⇒ definite). The orchestrator branches on that one
  signal. (.NET analogue: Resilience4j ≈ Polly; circuit breaker / retry / timeout policies.)

A `FlakyPartnerStub` (config `skyward.partner.fulfilment.mode = SUCCEED|FAIL|SLOW|…`) forces the
compensation path on demand for the demo.

---

## 5. Flow 3 — Strangler Fig (tier reads: legacy SOAP vs new REST)

**Story:** `GET /members/{id}/tier` is served by a legacy SOAP service *and* a new domain REST endpoint. An
edge facade routes between them — first **shadow-compare** (serve old, compare new on live traffic), then a
**config-driven percentage cutover** — and normalises both into one response shape.

### 5a. The routing decision (sticky per member)
`experience-api/.../tier/StranglerRouter.java`

```java
public boolean routesToLegacy(UUID memberId) {
    int bucket = Math.floorMod(memberId.hashCode(), 100);
    return bucket < properties.getLegacyPercent();     // 0 = all new, 100 = all legacy
}
```

**Sticky hash-by-member**, not per-request random: same member always takes the same path. Gives a
consistent member experience, **cohort-based rollback** (lower the % → whole cohorts move back), and
reproducibility ("member X is on the new path"). Real platforms bucket on a stable key (feature flags,
canaries). Per-request random hits the same population % but loses all three.

### 5b. Two migration modes in one facade
`experience-api/.../tier/TierFacade.java`

```java
public TierView resolve(UUID memberId) {
    if (properties.getShadow().isEnabled()) return resolveWithShadow(memberId);  // pre-cutover
    return router.select(memberId).tierFor(memberId);                            // cutover (% routing)
}

private TierView resolveWithShadow(UUID memberId) {
    TierProvider authoritative = authoritativeProvider();         // serve this (legacy initially)
    TierView served = authoritative.tierFor(memberId);            // its failure IS the caller's failure
    compareAgainstShadow(memberId, served, otherProvider);        // best-effort; never breaks the response
    return served;
}
```

Shadow comparison is **best-effort**: the shadow path's failure is recorded (`ShadowComparisonListener`,
logging today → a metric later), never propagated. A mismatch is logged for the migration team. This is how
you find where the new system disagrees with the old *before* serving anyone the new answer.

### 5c. Normalising two protocols + two "not found" conventions
- `DomainTierProvider` uses `RestClient` (Spring 6.1 fluent HTTP client) and maps a domain **404 → 404**.
- `LegacyTierProvider` uses `WebServiceTemplate` (Spring-WS SOAP client) and maps a SOAP **client fault →
  404**:

```java
try {
    GetMemberTierResponse response = (GetMemberTierResponse) legacyWebServiceTemplate.marshalSendAndReceive(request);
    return new TierView(memberId, response.getTier(), SOURCE, now());
} catch (SoapFaultClientException fault) {     // legacy's "not found" rides a SOAP fault on HTTP 500
    throw new MemberNotFoundException(memberId);   // → uniform edge 404
}
```

Both paths produce the same `TierView` (with a `source` field so you can *see* who answered), and the same
404 — so switching paths is invisible to callers.

### 5d. The legacy SOAP service (contract-first Spring-WS)
`adapter-legacy/...`

- `tiers.xsd` is the **contract**, published as a live WSDL at `/ws/tiers.wsdl` via `DefaultWsdl11Definition`.
- `LegacyTierEndpoint` is `@Endpoint`; `@PayloadRoot(namespace, localPart="GetMemberTierRequest")` routes by
  the **payload root element's qualified name** — *not* by URL. (This is the core SOAP vs REST mental shift:
  every message hits one `MessageDispatcherServlet` at `/ws/*`; dispatch is on the XML root element. XML
  namespaces are therefore load-bearing.)
- Not-found is a `@SoapFault(CLIENT)` exception.
- `LegacyTierRepository` is an **in-memory, independent system of record** — it can disagree with the new
  domain, which is what makes shadow-compare meaningful.
- `LegacySeedController` (`PUT /admin/members/{id}/tier`) is an **out-of-band** admin seam to load data; the
  SOAP *contract* stays frozen (you don't add features to the system you're retiring).

(.NET analogue: Spring-WS contract-first ≈ WCF with a WSDL/XSD-first contract; `Jaxb2Marshaller` ≈
`DataContractSerializer`; `WebServiceTemplate` ≈ a generated SOAP client / `SoapHttpClientProtocol`.)

---

## 6. Cross-cutting rules every flow upholds

- **Append-only ledger.** Never `UPDATE`/`DELETE` a ledger row; balance is derived. The `LedgerEntryRepository`
  deliberately exposes no mutation of existing rows.
- **Idempotency everywhere** under at-least-once delivery: accrual (idempotency key + UNIQUE), saga steps
  (status checks + `redemptionId`), projection (`processed_event` inbox).
- **No dual writes.** Anything that must reach Kafka goes through the outbox in the same DB tx.
- **No silent message loss.** `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` retry then route poison
  messages to `<topic>.DLT` (2 retries, 500ms apart).
- **Every external call is guarded** by Resilience4j with a defined failure behaviour.
- **Config-driven**: campaigns, routing %, shadow mode, partner mode, endpoints — all configuration.

---

## 7. Java / Spring / library nuances worth knowing

| Topic | What to know | .NET analogue |
|---|---|---|
| **DI** | **Constructor injection only**, `final` fields, no field injection. Beans are singletons by default. | ctor injection in ASP.NET Core |
| **`@Transactional` is a proxy** | Wraps the bean; **self-invocation bypasses it**. Cross-cutting steps go on a separate bean, called *through* the proxy. Same trap for `@Async`, Resilience4j, `@Cacheable`. | `TransactionScope` + interceptor that only fires across the proxy boundary |
| **JPA dirty checking** | A loaded (*managed*) entity's setter changes are flushed at commit **without** calling `save()`. The relay's `markPublished()` relies on this. `save()` on a managed entity is a harmless no-op-ish merge. | EF change tracking + `SaveChanges()` |
| **`JpaRepository` vs `Repository`** | Extend the **minimal** `Repository<T,ID>` and declare only the methods you want to *restrict* the surface (outbox/append-only). `JpaRepository` gives the full CRUD incl. delete. | restricting a `DbSet` via a custom repo |
| **Records** | Immutable DTOs/events (`PointsAccrued`, `TierView`, request bodies). Jackson binds them directly. | C# `record` |
| **Pessimistic lock** | `@Lock(PESSIMISTIC_WRITE)` on a query → `SELECT … FOR UPDATE`. Per-member mutex for reserve. | `SELECT … WITH (UPDLOCK)` |
| **Native query for `SKIP LOCKED`** | JPQL can't express `FOR UPDATE SKIP LOCKED`; use `@Query(nativeQuery=true)`. | raw SQL via Dapper/ADO |
| **Spring Kafka** | `KafkaTemplate.send()` returns a `CompletableFuture`; `.get(timeout)` makes it synchronous (relay waits for ack). `@KafkaListener` consumes. Error handling is a `DefaultErrorHandler` bean + DLT recoverer. | Confluent .NET producer/consumer |
| **Resilience4j** | Annotation **order** = `Retry→CircuitBreaker→TimeLimiter`. `@TimeLimiter` needs `CompletableFuture`. It cancels but can't interrupt a blocking call → use a **dedicated bounded executor**. Configured per-instance in YAML. | Polly policies (wrap order matters) |
| **Spring-WS (SOAP)** | Contract-first XSD→WSDL. Dispatch by **payload root QName**, not URL. `MessageDispatcherServlet` at `/ws/*` is separate from the MVC `DispatcherServlet`. `Jaxb2Marshaller` (un)marshals. `@SoapFault` for faults. | WCF service + `svcutil` |
| **`RestClient`** | Spring 6.1 synchronous fluent HTTP client; `.onStatus(...)` to map status→exception. Tested with `MockRestServiceServer`. | `HttpClient` + handlers |
| **Validation** | `@Valid` + `@NotBlank/@NotNull` on request records → 400 before the controller body. Unknown JSON enum value also → 400 (binding failure). Needs `spring-boot-starter-validation`. | `[ApiController]` model validation |
| **Config** | `@ConfigurationProperties(prefix=...)` bound classes/records + `@ConfigurationPropertiesScan`. Env overrides via `SKYWARD_…`. | `IOptions<T>` + config providers |
| **Virtual threads** | `spring.threads.virtual.enabled=true` on REST read paths — cheap blocking on downstream I/O (Java 21). | (no direct analogue; ≈ cheap async without async/await) |
| **Flyway** | Versioned SQL migrations own the schema; Hibernate `ddl-auto=validate` only checks, never alters. | EF migrations, but SQL-first |
| **`BigDecimal`** | Use it for the *final* points/money value + a single `HALF_UP` rounding; don't round per step with doubles. | `decimal` |
| **Testcontainers** | Real Postgres + Kafka in tests via a **singleton container** pattern + `@DynamicPropertySource` to inject URLs. SOAP tested with `MockWebServiceClient`/`MockWebServiceServer`. | Testcontainers for .NET |

---

## 8. What's done vs deliberate shortcuts

**Done & defensible:** all three flows end-to-end with tests (Testcontainers for infra); outbox crash-safety;
saga compensation + the timeout-after-fulfilment edge case + restart recovery; strangler routing + shadow
compare; idempotency on every ingress; DLQ; ADRs 0001–0003. Member enrolment + legacy seed make it
live-demoable.

**Deliberate shortcuts (with the production shape):**
- **In-memory legacy store** — stands in for a real legacy DB behind the SOAP service. Makes the strangler
  real without building a second datastore.
- **Open admin seed endpoint** on legacy — would be access-controlled / ops-only in production.
- **Polling outbox relay** — Debezium CDC is the production-grade version (ADR-0001).
- **Accrual duplicate race returns an error, not a graceful `DUPLICATE`** — correctness is preserved (no
  double credit); graceful handling is a refinement.
- **Stubbed partner + flaky toggle** — a real partner adapter would translate a real contract/auth.
- **No auth** — a stubbed header-based identity is the planned next step; real OIDC + mTLS noted as where it
  belongs. Member enrolment is intentionally thin (no `MemberEnrolled` event, no identity uniqueness).
- **3 deployables, not strict per-layer** — packaging simplification; layers scale independently in prod.

**Out of scope (by design):** booking/inventory/NDC, payments, a full IdP, a standalone API gateway, a web UI.

---

## 9. Where to find things (jump table)

| You want… | Look at |
|---|---|
| Atomic accrual + outbox write | `business-api/.../accrual/AccrualService.java` |
| The relay (SKIP LOCKED, at-least-once) | `domain-core/.../outbox/relay/OutboxRelay.java`, `OutboxEventRepository.java` |
| Idempotent projection (inbox) | `domain-core/.../balance/BalanceProjectionService.java` |
| DLQ / consumer error handling | `business-api/.../config/KafkaConsumerErrorConfig.java` |
| Saga orchestration + compensation | `business-api/.../redemption/RedemptionOrchestrator.java`, `RedemptionService.java` |
| Resilience4j partner call | `adapter-partner/.../PartnerFulfilmentGateway.java`, `ResilientPartnerFulfilmentClient.java` |
| Strangler routing + shadow | `experience-api/.../tier/StranglerRouter.java`, `TierFacade.java` |
| Legacy SOAP service | `adapter-legacy/.../LegacyTierEndpoint.java`, `WebServiceConfig.java`, `tiers.xsd` |
| The "why" (decisions) | `docs/adr/0001-…`, `0002-…`, `0003-…` |
```
