package com.skyward.experience.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.payload;
import static org.springframework.ws.test.client.ResponseCreators.withClientOrSenderFault;
import static org.springframework.ws.test.client.ResponseCreators.withPayload;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.test.client.MockWebServiceServer;
import org.springframework.xml.transform.StringSource;

/**
 * The legacy (SOAP) path of the facade, tested against {@link MockWebServiceServer} — the client-side
 * counterpart of the server test in adapter-legacy. It stubs the SOAP response bound to a real
 * {@link WebServiceTemplate}, so this proves the client marshals the correct request, parses the response
 * into a normalized {@link TierView} stamped {@code source=legacy}, and turns a SOAP <em>client fault</em>
 * (legacy's "not found") into a {@link MemberNotFoundException} — the fault-to-404 translation seam.
 */
class LegacyTierProviderTest {

    private static final String NS = "http://skyward.com/legacy/tiers";

    private static WebServiceTemplate webServiceTemplate() throws Exception {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setPackagesToScan("com.skyward.experience.legacy.client");
        marshaller.afterPropertiesSet();
        WebServiceTemplate template = new WebServiceTemplate(marshaller);
        template.setDefaultUri("http://legacy/ws");
        return template;
    }

    @Test
    void resolvesTierFromLegacyService() throws Exception {
        WebServiceTemplate template = webServiceTemplate();
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        LegacyTierProvider provider = new LegacyTierProvider(template);
        UUID memberId = UUID.randomUUID();

        server.expect(payload(new StringSource(
                        "<tns:GetMemberTierRequest xmlns:tns=\"" + NS + "\">"
                                + "<tns:memberId>" + memberId + "</tns:memberId>"
                                + "</tns:GetMemberTierRequest>")))
                .andRespond(withPayload(new StringSource(
                        "<tns:GetMemberTierResponse xmlns:tns=\"" + NS + "\">"
                                + "<tns:memberId>" + memberId + "</tns:memberId>"
                                + "<tns:tier>SILVER</tns:tier>"
                                + "</tns:GetMemberTierResponse>")));

        TierView view = provider.tierFor(memberId);

        assertThat(view.memberId()).isEqualTo(memberId);
        assertThat(view.tier()).isEqualTo("SILVER");
        assertThat(view.source()).isEqualTo("legacy");
        server.verify();
    }

    @Test
    void translatesSoapClientFaultIntoMemberNotFound() throws Exception {
        WebServiceTemplate template = webServiceTemplate();
        MockWebServiceServer server = MockWebServiceServer.createServer(template);
        LegacyTierProvider provider = new LegacyTierProvider(template);
        UUID memberId = UUID.randomUUID();

        server.expect(payload(new StringSource(
                        "<tns:GetMemberTierRequest xmlns:tns=\"" + NS + "\">"
                                + "<tns:memberId>" + memberId + "</tns:memberId>"
                                + "</tns:GetMemberTierRequest>")))
                .andRespond(withClientOrSenderFault("member not found", Locale.ENGLISH));

        assertThatThrownBy(() -> provider.tierFor(memberId))
                .isInstanceOf(MemberNotFoundException.class);
    }
}
