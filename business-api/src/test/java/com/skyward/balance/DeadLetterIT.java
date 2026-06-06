package com.skyward.balance;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.common.event.Topics;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/**
 * Poison-message handling: a message that cannot be parsed is retried a few times and then routed to
 * the dead-letter topic rather than blocking the partition forever or being silently dropped.
 */
@SpringBootTest
class DeadLetterIT extends AbstractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void poisonMessageIsRoutedToDeadLetterTopic() {
        String key = UUID.randomUUID().toString();
        String poison = "this-is-not-valid-json";

        kafkaTemplate.send(Topics.POINTS_ACCRUED, key, poison);

        ConsumerRecord<String, String> dead = findDeadLetterFor(key);
        assertThat(dead).isNotNull();
        assertThat(dead.value()).isEqualTo(poison);
    }

    private ConsumerRecord<String, String> findDeadLetterFor(String key) {
        var props = KafkaTestUtils.consumerProps(
                kafkaBootstrapServers(), "dlt-it-" + UUID.randomUUID(), "true");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer()) {
            consumer.subscribe(List.of(Topics.POINTS_ACCRUED_DLT));
            ConsumerRecords<String, String> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(20));
            for (ConsumerRecord<String, String> record : records) {
                if (key.equals(record.key())) {
                    return record;
                }
            }
            return null;
        }
    }
}
