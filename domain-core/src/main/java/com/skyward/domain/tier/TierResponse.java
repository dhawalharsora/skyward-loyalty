package com.skyward.domain.tier;

import com.skyward.domain.member.Tier;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read model for a member's tier on the <b>new</b> domain path of the strangler.
 *
 * <p>{@code source} names which system answered ("domain"); the legacy SOAP path reports "legacy". The
 * strangler facade surfaces this so a caller — or a shadow-compare log — can see <em>who</em> served the
 * tier, which is exactly what you want visible during a migration cutover.
 */
public record TierResponse(UUID memberId, Tier tier, String source, OffsetDateTime asOf) {}
