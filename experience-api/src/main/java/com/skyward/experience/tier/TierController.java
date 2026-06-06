package com.skyward.experience.tier;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The strangler routing facade's public endpoint. It owns no tier logic: it delegates to the
 * {@link TierFacade}, which resolves the tier via shadow-compare or percentage routing per config, and
 * returns the normalized {@link TierView}. This is exactly why the strangler lives in the Experience
 * layer — it is an edge/traffic concern, not a domain rule.
 */
@RestController
@RequestMapping("/members")
public class TierController {

    private final TierFacade tierFacade;

    public TierController(TierFacade tierFacade) {
        this.tierFacade = tierFacade;
    }

    @GetMapping("/{id}/tier")
    public TierView tier(@PathVariable("id") UUID id) {
        return tierFacade.resolve(id);
    }
}
