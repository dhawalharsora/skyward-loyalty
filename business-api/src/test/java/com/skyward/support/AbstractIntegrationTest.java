package com.skyward.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests using the <b>singleton container</b> pattern.
 *
 * <p>Postgres and Kafka are {@code static} and started <em>once</em>, then shared by every test class
 * that extends this base (Testcontainers' Ryuk reaps them at JVM exit). This is dramatically faster
 * than the per-class {@code @Testcontainers} lifecycle as the suite grows, while still running against
 * real infrastructure. Lives in the {@code business-api} ("core") module — the bootable app.
 */
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }

    /** Bootstrap servers of the shared Kafka container, for tests that build their own clients. */
    protected static String kafkaBootstrapServers() {
        return KAFKA.getBootstrapServers();
    }

    @DynamicPropertySource
    static void registerInfraProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);

        // Neutralise the SCHEDULED saga recovery during tests: a huge min-age means the background
        // scheduler never picks up freshly-created test sagas. Tests that exercise recovery call
        // RedemptionRecoveryService.recoverStuckSagas(Duration.ZERO) explicitly for determinism.
        registry.add("skyward.redemption.recovery.min-age-seconds", () -> "86400");
        registry.add("skyward.redemption.recovery.poll-interval-ms", () -> "3600000");
    }
}
