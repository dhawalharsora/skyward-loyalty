package com.skyward.accrual;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.domain.ledger.LedgerEntry;
import com.skyward.domain.ledger.LedgerEntryRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.domain.outbox.OutboxEvent;
import com.skyward.domain.outbox.OutboxEventRepository;
import com.skyward.support.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Proves the transactional outbox write: a single accrual writes BOTH a ledger entry and an outbox
 * event, and a replayed accrual (same idempotency key) credits nothing extra.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccrualIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MemberRepository members;

    @Autowired
    private LedgerEntryRepository ledger;

    @Autowired
    private OutboxEventRepository outbox;

    @Test
    void accrualWritesLedgerEntryAndOutboxEvent() {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD)); // x1.5

        AccrualRequest request = new AccrualRequest(member.getId(), 1_000, "flight:SY123", "idem-1");
        ResponseEntity<AccrualResponse> response =
                rest.postForEntity("/accruals", request, AccrualResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AccrualStatus.ACCRUED);
        assertThat(response.getBody().earnedPoints()).isEqualTo(1_500L); // 1000 * 1.5

        // Ledger: exactly one entry, with the earned amount and the idempotency key.
        List<LedgerEntry> entries = ledger.findByMemberIdOrderByCreatedAtAsc(member.getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getAmount()).isEqualTo(1_500L);

        // Outbox: exactly one PointsAccrued event for this member, written in the same transaction.
        List<OutboxEvent> events = outbox.findByAggregateId(member.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("PointsAccrued");
        assertThat(events.get(0).getPublishedAt()).isNull(); // not yet relayed
        assertThat(events.get(0).getPayload()).contains("\"points\":1500");
    }

    @Test
    void duplicateIdempotencyKeyDoesNotDoubleCredit() {
        Member member = members.save(Member.enrol("Grace Hopper", Tier.SILVER));
        AccrualRequest request = new AccrualRequest(member.getId(), 1_000, "flight:SY9", "idem-dup");

        ResponseEntity<AccrualResponse> first =
                rest.postForEntity("/accruals", request, AccrualResponse.class);
        ResponseEntity<AccrualResponse> second =
                rest.postForEntity("/accruals", request, AccrualResponse.class);

        assertThat(first.getBody().status()).isEqualTo(AccrualStatus.ACCRUED);
        assertThat(second.getBody().status()).isEqualTo(AccrualStatus.DUPLICATE);
        // Same earned points reported back on the replay.
        assertThat(second.getBody().earnedPoints()).isEqualTo(first.getBody().earnedPoints());

        // Still exactly one ledger entry and one outbox event — no double credit, no double publish.
        assertThat(ledger.findByMemberIdOrderByCreatedAtAsc(member.getId())).hasSize(1);
        assertThat(outbox.findByAggregateId(member.getId())).hasSize(1);
    }

    @Test
    void unknownMemberReturns404() {
        AccrualRequest request =
                new AccrualRequest(UUID.randomUUID(), 1_000, "flight:SY1", "idem-unknown");
        ResponseEntity<String> response = rest.postForEntity("/accruals", request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
