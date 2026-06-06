package com.skyward.domain.tier;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Real-time (synchronous REST) read of a member's tier — the <b>new</b> path the strangler routes to.
 * Same {@code /members/{id}} resource family as the balance read; the routing facade in experience-api
 * fronts this with a config-driven split against the legacy SOAP service.
 */
@RestController
@RequestMapping("/members")
public class MemberTierController {

    private final MemberTierService tierService;

    public MemberTierController(MemberTierService tierService) {
        this.tierService = tierService;
    }

    @GetMapping("/{id}/tier")
    public TierResponse tier(@PathVariable("id") UUID id) {
        return tierService.tierFor(id);
    }
}
