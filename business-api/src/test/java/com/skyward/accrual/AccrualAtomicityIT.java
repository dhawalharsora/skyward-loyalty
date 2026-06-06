package com.skyward.accrual;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.skyward.domain.ledger.LedgerEntryRepository;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.member.Tier;
import com.skyward.domain.outbox.OutboxEvent;
import com.skyward.domain.outbox.OutboxEventRepository;
import com.skyward.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

/**
 * The honest atomicity proof. We replace the outbox repository with a mock that throws when saving,
 * then perform an accrual. Because the ledger write and the outbox write share one transaction, the
 * outbox failure must roll back the ledger write too — leaving no orphaned ledger entry. This is the
 * guarantee that makes the outbox correct: you never get the business row without its event.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccrualAtomicityIT extends AbstractIntegrationTest {

    @MockBean
    private OutboxEventRepository outbox;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private MemberRepository members;

    @Autowired
    private LedgerEntryRepository ledger;

    @Test
    void outboxWriteFailureRollsBackTheLedgerWrite() {
        given(outbox.save(any(OutboxEvent.class)))
                .willThrow(new RuntimeException("simulated outbox failure"));

        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD));
        AccrualRequest request =
                new AccrualRequest(member.getId(), 1_000, "flight:SY1", "idem-atomic");

        ResponseEntity<String> response = rest.postForEntity("/accruals", request, String.class);

        // The request fails...
        assertThat(response.getStatusCode().is5xxServerError()).isTrue();
        // ...and crucially, NO ledger entry was persisted: the whole transaction rolled back.
        assertThat(ledger.findByMemberIdOrderByCreatedAtAsc(member.getId())).isEmpty();
    }
}
