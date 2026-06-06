package com.skyward.experience.tier;

import com.skyward.experience.legacy.client.GetMemberTierRequest;
import com.skyward.experience.legacy.client.GetMemberTierResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

/**
 * The <b>legacy</b> path: reads tier from the old SOAP service via {@link WebServiceTemplate}. Maps the
 * SOAP response into a normalized {@link TierView} stamped {@code source=legacy}.
 *
 * <p>The translation that matters: the legacy service signals "unknown member" as a SOAP <em>client</em>
 * fault, which the template raises as {@link SoapFaultClientException}. We turn that into the same
 * {@link MemberNotFoundException} the REST path uses, so the edge's not-found contract is uniform. A
 * richer legacy fault taxonomy would inspect the fault code/string here; this contract only emits the
 * one client fault, so catching it is sufficient and intentional.
 */
@Component
public class LegacyTierProvider implements TierProvider {

    static final String SOURCE = "legacy";

    private final WebServiceTemplate legacyWebServiceTemplate;

    public LegacyTierProvider(WebServiceTemplate legacyWebServiceTemplate) {
        this.legacyWebServiceTemplate = legacyWebServiceTemplate;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public TierView tierFor(UUID memberId) {
        GetMemberTierRequest request = new GetMemberTierRequest();
        request.setMemberId(memberId.toString());

        try {
            GetMemberTierResponse response = (GetMemberTierResponse)
                    legacyWebServiceTemplate.marshalSendAndReceive(request);
            return new TierView(
                    memberId, response.getTier(), SOURCE, OffsetDateTime.now(ZoneOffset.UTC));
        } catch (SoapFaultClientException fault) {
            throw new MemberNotFoundException(memberId);
        }
    }
}
