package com.skyward.legacy;

/** Confirmation returned by the admin seed endpoint: what is now stored for the member. */
public record LegacyTierView(String memberId, String tier) {}
