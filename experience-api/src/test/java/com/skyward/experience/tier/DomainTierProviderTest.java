package com.skyward.experience.tier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * The new (REST) path of the facade, tested against {@link MockRestServiceServer} — a stubbed HTTP server
 * bound to the {@link RestClient} so no real domain service is needed. Proves the client builds the right
 * request, parses the JSON into a normalized {@link TierView} stamped {@code source=domain}, and turns a
 * 404 from the domain service into a {@link MemberNotFoundException} the facade can surface as 404.
 */
class DomainTierProviderTest {

    private DomainTierProvider provider(MockRestServiceServerHolder holder) {
        RestClient.Builder builder = RestClient.builder();
        holder.server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("http://core").build();
        return new DomainTierProvider(client);
    }

    /** Tiny holder so the test can both build the client and keep a handle on the mock server. */
    static final class MockRestServiceServerHolder {
        MockRestServiceServer server;
    }

    @Test
    void resolvesTierFromDomainService() {
        MockRestServiceServerHolder holder = new MockRestServiceServerHolder();
        DomainTierProvider provider = provider(holder);
        UUID memberId = UUID.randomUUID();

        holder.server.expect(requestTo("http://core/members/" + memberId + "/tier"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"memberId\":\"" + memberId + "\",\"tier\":\"GOLD\",\"source\":\"domain\"}",
                        MediaType.APPLICATION_JSON));

        TierView view = provider.tierFor(memberId);

        assertThat(view.memberId()).isEqualTo(memberId);
        assertThat(view.tier()).isEqualTo("GOLD");
        assertThat(view.source()).isEqualTo("domain");
        holder.server.verify();
    }

    @Test
    void translatesDomain404IntoMemberNotFound() {
        MockRestServiceServerHolder holder = new MockRestServiceServerHolder();
        DomainTierProvider provider = provider(holder);
        UUID memberId = UUID.randomUUID();

        holder.server.expect(requestTo("http://core/members/" + memberId + "/tier"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> provider.tierFor(memberId))
                .isInstanceOf(MemberNotFoundException.class);
    }
}
