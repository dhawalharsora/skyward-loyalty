package com.skyward.legacy;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Spring-WS wiring for the legacy SOAP service.
 *
 * <ul>
 *   <li>{@link MessageDispatcherServlet} mounted at {@code /ws/*} — the SOAP front door (separate from
 *       any Spring MVC dispatcher), routing messages to {@code @Endpoint} beans by payload root.</li>
 *   <li>{@link DefaultWsdl11Definition} publishes a real WSDL at {@code /ws/tiers.wsdl}, generated from
 *       the XSD — so clients can be generated contract-first against a live, inspectable contract.</li>
 *   <li>{@link Jaxb2Marshaller} (un)marshals the hand-written binding classes in {@code .tiers}.</li>
 * </ul>
 */
@EnableWs
@Configuration
public class WebServiceConfig {

    private static final String NAMESPACE = "http://skyward.com/legacy/tiers";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        // Rewrites the WSDL's soap:address to the request URL, so the published location is correct
        // regardless of host/port the legacy service runs on.
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /** Exposes the WSDL at /ws/tiers.wsdl (bean name + ".wsdl"). */
    @Bean(name = "tiers")
    public DefaultWsdl11Definition tiersWsdl(XsdSchema tiersSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("TiersPort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace(NAMESPACE);
        definition.setSchema(tiersSchema);
        return definition;
    }

    @Bean
    public XsdSchema tiersSchema() {
        return new SimpleXsdSchema(new ClassPathResource("tiers.xsd"));
    }

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setPackagesToScan("com.skyward.legacy.tiers");
        return marshaller;
    }
}
