package com.skyward.legacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

import java.util.Map;
import java.util.UUID;
import javax.xml.transform.Source;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

/**
 * The out-of-band seed path: an admin REST endpoint loads tier data into the legacy store, which the
 * frozen SOAP <em>contract</em> then serves. This is the demo seam — enrol a member in core, seed the
 * same id here with a (possibly different) tier, then let the strangler route to it. The test asserts the
 * full path: REST seed in, SOAP read out.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacySeedControllerIT {

    private static final String NS = "http://skyward.com/legacy/tiers";
    private static final Map<String, String> NS_MAP = Map.of("tns", NS);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ApplicationContext applicationContext;

    private MockWebServiceClient soap;

    @BeforeEach
    void setUp() {
        soap = MockWebServiceClient.createClient(applicationContext);
    }

    @Test
    void seededTierIsServedBySoapEndpoint() {
        String memberId = UUID.randomUUID().toString();

        ResponseEntity<String> seed = rest.exchange(
                "/admin/members/{id}/tier",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("tier", "SILVER")),
                String.class,
                memberId);
        assertThat(seed.getStatusCode()).isEqualTo(HttpStatus.OK);

        Source request = new StringSource(
                "<tns:GetMemberTierRequest xmlns:tns=\"" + NS + "\">"
                        + "<tns:memberId>" + memberId + "</tns:memberId>"
                        + "</tns:GetMemberTierRequest>");

        soap.sendRequest(withPayload(request))
                .andExpect(noFault())
                .andExpect(xpath("//tns:tier/text()", NS_MAP).evaluatesTo("SILVER"));
    }

    @Test
    void putOverwritesAnExistingTier() {
        String memberId = UUID.randomUUID().toString();

        rest.put("/admin/members/{id}/tier", Map.of("tier", "GOLD"), memberId);
        rest.put("/admin/members/{id}/tier", Map.of("tier", "PLATINUM"), memberId);

        Source request = new StringSource(
                "<tns:GetMemberTierRequest xmlns:tns=\"" + NS + "\">"
                        + "<tns:memberId>" + memberId + "</tns:memberId>"
                        + "</tns:GetMemberTierRequest>");

        soap.sendRequest(withPayload(request))
                .andExpect(xpath("//tns:tier/text()", NS_MAP).evaluatesTo("PLATINUM"));
    }

    @Test
    void blankTierIsRejectedWith400() {
        ResponseEntity<String> response = rest.exchange(
                "/admin/members/{id}/tier",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("tier", "")),
                String.class,
                UUID.randomUUID().toString());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
