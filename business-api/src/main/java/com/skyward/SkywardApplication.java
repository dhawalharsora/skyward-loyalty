package com.skyward;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Boot entry point for the <b>core</b> deployable (business + domain).
 *
 * <p>Lives in the root {@code com.skyward} package so component scanning, JPA entity scanning, and
 * Spring Data repository scanning all default to {@code com.skyward.*} — covering both this module
 * ({@code com.skyward.business} etc.) and the {@code domain-core} library ({@code com.skyward.domain}).
 * The domain layer ships as a plain library on this app's classpath; its beans (repositories, the
 * outbox relay, domain services) run inside this single process.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SkywardApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkywardApplication.class, args);
    }
}
