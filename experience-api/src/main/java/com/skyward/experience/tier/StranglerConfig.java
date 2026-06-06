package com.skyward.experience.tier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.web.client.RestClient;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * Wires the two backend clients the facade routes between, both pointed by {@link StranglerProperties}:
 * a {@link RestClient} for the new domain service and a {@link WebServiceTemplate} (with a JAXB
 * marshaller over the edge's own client bindings) for the legacy SOAP service.
 */
@Configuration
public class StranglerConfig {

    @Bean
    public RestClient domainRestClient(RestClient.Builder builder, StranglerProperties properties) {
        return builder.baseUrl(properties.getDomainBaseUrl()).build();
    }

    @Bean
    public Jaxb2Marshaller legacyClientMarshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setPackagesToScan("com.skyward.experience.legacy.client");
        return marshaller;
    }

    @Bean
    public WebServiceTemplate legacyWebServiceTemplate(
            Jaxb2Marshaller legacyClientMarshaller, StranglerProperties properties) {
        WebServiceTemplate template = new WebServiceTemplate(legacyClientMarshaller);
        template.setDefaultUri(properties.getLegacyUri());
        return template;
    }
}
