package com.skyward.adapter.partner;

/**
 * Thrown by the partner when it <em>definitely</em> rejects a fulfilment (e.g. reward unavailable). A
 * definite outcome — the reward was not issued — so the saga can safely compensate. Distinct from
 * timeouts/connection errors, which are indeterminate.
 */
public class PartnerRejectedException extends RuntimeException {

    public PartnerRejectedException(String message) {
        super(message);
    }
}
