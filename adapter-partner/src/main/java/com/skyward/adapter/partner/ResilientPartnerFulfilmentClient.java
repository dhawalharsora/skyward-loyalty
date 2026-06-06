package com.skyward.adapter.partner;

import com.skyward.common.partner.FulfilmentException;
import com.skyward.common.partner.FulfilmentRequest;
import com.skyward.common.partner.FulfilmentResult;
import com.skyward.common.partner.PartnerFulfilmentClient;
import java.util.concurrent.CompletionException;
import org.springframework.stereotype.Component;

/**
 * The {@link PartnerFulfilmentClient} port implementation. Presents a synchronous API to the saga by
 * joining the resilient async call, and normalises every failure (rejection, timeout, open circuit)
 * into a {@link FulfilmentException} so the orchestrator has a single failure signal to compensate on.
 */
@Component
public class ResilientPartnerFulfilmentClient implements PartnerFulfilmentClient {

    private final PartnerFulfilmentGateway gateway;

    public ResilientPartnerFulfilmentClient(PartnerFulfilmentGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public FulfilmentResult fulfil(FulfilmentRequest request) {
        try {
            return gateway.fulfil(request).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            // Only an explicit partner rejection is "definite" (safe to compensate). Everything else —
            // timeout, open circuit, connection error — is indeterminate: the outcome is unknown.
            boolean indeterminate = !(cause instanceof PartnerRejectedException);
            throw new FulfilmentException(
                    "partner fulfilment failed for redemption " + request.redemptionId(),
                    cause, indeterminate);
        }
    }
}
