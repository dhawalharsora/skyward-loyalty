package com.skyward.legacy;

import com.skyward.legacy.tiers.GetMemberTierRequest;
import com.skyward.legacy.tiers.GetMemberTierResponse;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * The legacy SOAP tier endpoint. {@code @PayloadRoot} routes any message whose payload root element is
 * {@code {http://skyward.com/legacy/tiers}GetMemberTierRequest} to this handler — the SOAP analogue of
 * a URL route. JAXB (un)marshals the request/response via the configured marshaller.
 *
 * <p>No business logic lives here beyond the lookup; this module is an adapter representing old SOA.
 */
@Endpoint
public class LegacyTierEndpoint {

    private static final String NAMESPACE = "http://skyward.com/legacy/tiers";

    private final LegacyTierRepository legacyTiers;

    public LegacyTierEndpoint(LegacyTierRepository legacyTiers) {
        this.legacyTiers = legacyTiers;
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "GetMemberTierRequest")
    @ResponsePayload
    public GetMemberTierResponse getMemberTier(@RequestPayload GetMemberTierRequest request) {
        String memberId = request.getMemberId();
        String tier = legacyTiers.findTier(memberId)
                .orElseThrow(() -> new LegacyMemberNotFoundException(memberId));

        GetMemberTierResponse response = new GetMemberTierResponse();
        response.setMemberId(memberId);
        response.setTier(tier);
        return response;
    }
}
