package com.skyward.domain.member;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a member id does not exist. {@code @ResponseStatus(NOT_FOUND)} makes Spring MVC translate
 * it to an HTTP 404 automatically — distinguishing "no such member" from "member with zero balance".
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class MemberNotFoundException extends RuntimeException {

    public MemberNotFoundException(UUID memberId) {
        super("Member not found: " + memberId);
    }
}
