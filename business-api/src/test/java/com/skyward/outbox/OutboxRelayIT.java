package com.skyward.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.skyward.accrual.AccrualRequest;
import com.skyward.accrual.AccrualService;
import com.skyward.common.event.Topics;
import com.skyward.domain.member.Member;
import com.skyward.domain.member.MemberRepository;
import com.skyward.domain.outbox.OutboxEvent;
import com.skyward.domain.outbox.OutboxEventRepository;
import com.skyward.domain.member.Tier;
import com.skyward.support.AbstractIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Proves the relay end of the outbox: an accrual leaves an unpublished outbox row, and the scheduled
 * relay publishes it to Kafka (keyed by member id) and stamps {@code published_at}.
 */
@SpringBootTest
class OutboxRelayIT extends AbstractIntegrationTest {

    @Autowired
    private AccrualService accrualService;

    @Autowired
    private MemberRepository members;

    @Autowired
    private OutboxEventRepository outbox;

    @Test
    void relayPublishesUnpublishedEventsAndMarksThemPublished() {
        Member member = members.save(Member.enrol("Ada Lovelace", Tier.GOLD)); // x1.5
        accrualService.accrue(new AccrualRequest(member.getId(), 1_000, "flight:SY1", "relay-1"));

        // The relay runs on a timer; wait until it has stamped the outbox row as published.
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            List<OutboxEvent> events = outbox.findByAggregateId(member.getId());
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getPublishedAt()).isNotNull();
        });

        // The event must actually be on the Kafka topic, keyed by member id, with the right payload.
        ConsumerRecord<String, String> record = findRecordForMember(member.getId());
        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(member.getId().toString());
        assertThat(record.value()).contains("\"points\":1500");
    }

    private ConsumerRecord<String, String> findRecordForMember(UUID memberId) {
        var props = KafkaTestUtils.consumerProps(kafkaBootstrapServers(), "relay-it-" + UUID.randomUUID(), "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            consumer.subscribe(List.of(Topics.POINTS_ACCRUED));
            // Earliest offset + fresh group: reads all events on the topic; filter to ours by key.
            ConsumerRecords<String, String> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));
            for (ConsumerRecord<String, String> record : records) {
                if (memberId.toString().equals(record.key())) {
                    return record;
                }
            }
            return null;
        }
    }
}
