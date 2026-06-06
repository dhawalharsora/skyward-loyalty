package com.skyward.experience.tier;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The <b>new</b> path: reads tier from the new domain service over REST. Maps the JSON into a normalized
 * {@link TierView} stamped {@code source=domain}, and translates the domain service's 404 into
 * {@link MemberNotFoundException}. The {@code source} label is set here (not trusted from the payload) so
 * "who served it" always reflects the path actually taken.
 */
@Component
public class DomainTierProvider implements TierProvider {

    static final String SOURCE = "domain";

    private final RestClient domainRestClient;

    public DomainTierProvider(RestClient domainRestClient) {
        this.domainRestClient = domainRestClient;
    }

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public TierView tierFor(UUID memberId) {
        DomainTierResponse body = domainRestClient.get()
                .uri("/members/{id}/tier", memberId)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {
                    throw new MemberNotFoundException(memberId);
                })
                .body(DomainTierResponse.class);

        return new TierView(memberId, body.tier(), SOURCE, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /** Minimal view of the domain service's JSON; extra fields (source, asOf) are ignored on parse. */
    record DomainTierResponse(UUID memberId, String tier) {}
}
