package com.skyward;

import static org.assertj.core.api.Assertions.assertThat;

import com.skyward.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Smoke test: the core deployable boots against real Postgres + real Kafka (KRaft). Because the
 * context starts, Flyway migrations (owned by the domain-core library) apply against a real Postgres
 * and the Kafka client resolves a broker.
 */
@SpringBootTest
class ContextLoadsIT extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isPositive();
    }
}
