package com.skyward.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer error handling with a dead-letter topic. Spring Boot wires this single {@code
 * CommonErrorHandler} bean into the auto-configured listener container factory.
 *
 * <p>On a processing failure the record is retried {@code 2} times (500ms apart); if it still fails it
 * is published by the {@link DeadLetterPublishingRecoverer} to {@code <topic>.DLT}. This bounds poison
 * messages: they neither block the partition forever nor get silently dropped.
 */
@Configuration
public class KafkaConsumerErrorConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        // 2 retries after the first attempt, then dead-letter.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 2L));
    }
}
