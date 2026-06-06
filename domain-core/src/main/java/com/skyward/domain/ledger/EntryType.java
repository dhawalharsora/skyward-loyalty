package com.skyward.domain.ledger;

/** Direction of a ledger entry. Magnitude is stored separately and is always positive. */
public enum EntryType {
    /** Points credited to a member (e.g. from a flight or partner activity). */
    EARN,
    /** Points debited from a member (e.g. redeeming a reward). */
    BURN
}
