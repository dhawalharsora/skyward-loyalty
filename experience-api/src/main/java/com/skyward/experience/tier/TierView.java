package com.skyward.experience.tier;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The facade's normalized tier response. Both routing paths (legacy SOAP, new REST) are mapped to this
 * single shape, so a caller gets an identical contract regardless of which system served the request.
 *
 * <p>{@code source} names the path that answered ("legacy" or "domain") — deliberately exposed so the
 * cutover is observable: during a migration you want to <em>see</em> who served each request.
 */
public record TierView(UUID memberId, String tier, String source, OffsetDateTime asOf) {}
