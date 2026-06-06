package com.skyward.legacy;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Out-of-band admin/seed surface for the legacy store. Deliberately separate from the SOAP business
 * contract (which stays frozen — you don't add features to the system you're retiring); this represents
 * the data-loading seam a real legacy system has (a DB load or an ops tool). In production it would be
 * access-controlled; here it is open for demo convenience.
 *
 * <p>PUT semantics: setting a member's tier is an idempotent upsert.
 */
@RestController
public class LegacySeedController {

    private final LegacyTierRepository legacyTiers;

    public LegacySeedController(LegacyTierRepository legacyTiers) {
        this.legacyTiers = legacyTiers;
    }

    @PutMapping("/admin/members/{id}/tier")
    public LegacyTierView seed(
            @PathVariable("id") String memberId, @RequestBody LegacySeedTierRequest request) {
        if (request.tier() == null || request.tier().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tier must not be blank");
        }
        legacyTiers.put(memberId, request.tier());
        return new LegacyTierView(memberId, request.tier());
    }
}
