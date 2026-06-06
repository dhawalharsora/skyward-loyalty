package com.skyward.experience.tier;

import java.util.UUID;

/**
 * A source of member tier data behind the strangler facade. Implementations hide the protocol (REST vs
 * SOAP) and the backend's not-found convention, returning a normalized {@link TierView} or throwing
 * {@link MemberNotFoundException}. The router picks one implementation per request.
 */
public interface TierProvider {

    TierView tierFor(UUID memberId);

    /** Stable label for the path this provider represents ("legacy" or "domain"); used for routing
     * choice, response stamping, and shadow-compare reporting. */
    String source();
}
