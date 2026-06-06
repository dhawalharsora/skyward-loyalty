package com.skyward.config;

import com.skyward.common.event.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics so they are created explicitly on startup (via the auto-configured KafkaAdmin)
 * rather than relying on broker auto-creation. Replication factor 1 fits the single-broker dev/test
 * setup; partitions enable per-key parallelism while keeping per-member ordering (events are keyed by
 * member id).
 */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic pointsAccruedTopic() {
        return TopicBuilder.name(Topics.POINTS_ACCRUED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic pointsAccruedDeadLetterTopic() {
        // Same partition count as the source so the recoverer can preserve the original partition.
        return TopicBuilder.name(Topics.POINTS_ACCRUED_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic pointsBurnedTopic() {
        return TopicBuilder.name(Topics.POINTS_BURNED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic pointsBurnedDeadLetterTopic() {
        return TopicBuilder.name(Topics.POINTS_BURNED_DLT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
