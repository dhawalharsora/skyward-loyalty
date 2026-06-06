package com.skyward.experience.tier;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Not-found at the edge, surfaced as HTTP 404. Each provider translates its backend's own not-found signal
 * into this one exception — a domain REST 404 and a legacy SOAP <em>client fault</em> both land here — so
 * the facade presents one consistent not-found contract no matter which path was taken.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(UUID memberId) {
        super("Member not found: " + memberId);
    }
}
