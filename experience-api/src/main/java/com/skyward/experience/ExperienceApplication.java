package com.skyward.experience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Boot entry point for the <b>experience-api</b> edge deployable. Hosts the strangler routing facade and
 * nothing else of substance: it owns no data and no business rules, it routes {@code GET /members/{id}/tier}
 * traffic between the legacy SOAP service and the new domain service. In production this layer sits behind
 * an API gateway (auth, rate limiting); here it is the thin BFF/edge.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ExperienceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExperienceApplication.class, args);
    }
}
