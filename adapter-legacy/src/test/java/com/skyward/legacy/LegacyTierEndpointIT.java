package com.skyward.legacy;

import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import java.util.Map;
import java.util.UUID;
import javax.xml.transform.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

/**
 * Drives the legacy SOAP tier service through a real SOAP message exchange. {@link MockWebServiceClient}
 * sends an actual XML payload into the {@code MessageDispatcherServlet}/endpoint pipeline (marshalling,
 * payload-root routing, fault handling) and asserts the response payload by XPath — the closest thing to
 * a real SOAP call without opening a socket. This represents how the old SOA is exercised; the strangler
 * facade (slice 4.3) will call it over HTTP with a SOAP client.
 */
@SpringBootTest
class LegacyTierEndpointIT {

    private static final String NS = "http://skyward.com/legacy/tiers";
    private static final Map<String, String> NS_MAP = Map.of("tns", NS);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private LegacyTierRepository legacyTiers;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(applicationContext);
    }

    @Test
    void returnsTierForKnownMember() {
        String memberId = UUID.randomUUID().toString();
        legacyTiers.put(memberId, "GOLD");

        Source request = new StringSource(
                "<tns:GetMemberTierRequest xmlns:tns=\"" + NS + "\">"
                        + "<tns:memberId>" + memberId + "</tns:memberId>"
                        + "</tns:GetMemberTierRequest>");

        client.sendRequest(withPayload(request))
                .andExpect(noFault())
                .andExpect(xpath("//tns:memberId/text()", NS_MAP).evaluatesTo(memberId))
                .andExpect(xpath("//tns:tier/text()", NS_MAP).evaluatesTo("GOLD"));
    }

    @Test
    void unknownMemberReturnsSoapClientFault() {
        Source request = new StringSource(
                "<tns:GetMemberTierRequest xmlns:tns=\"" + NS + "\">"
                        + "<tns:memberId>" + UUID.randomUUID() + "</tns:memberId>"
                        + "</tns:GetMemberTierRequest>");

        // A client (sender) fault: the request was well-formed but names an unknown member. This is the
        // legacy contract's way of saying 404 — the facade must translate it back to a REST 404.
        client.sendRequest(withPayload(request))
                .andExpect(clientOrSenderFault());
    }
}
