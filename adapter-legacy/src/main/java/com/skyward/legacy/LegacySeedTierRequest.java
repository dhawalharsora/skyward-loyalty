package com.skyward.legacy;

/** Body of the admin seed call: the tier to store for a member. Kept a free string deliberately — the
 * legacy store can hold values the new system wouldn't recognise, which is part of the drift it models. */
public record LegacySeedTierRequest(String tier) {}
