package com.skyward.legacy;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/**
 * Thrown when the legacy store has no tier for the requested member. {@code @SoapFault(CLIENT)} makes
 * Spring-WS render it as a SOAP <em>client</em> (sender) fault — the legacy contract's equivalent of a
 * 404. The strangler facade must recognise this fault and translate it back to an HTTP 404 so both
 * routing paths present the same not-found contract.
 */
@SoapFault(faultCode = FaultCode.CLIENT)
public class LegacyMemberNotFoundException extends RuntimeException {

    public LegacyMemberNotFoundException(String memberId) {
        super("Legacy tier service has no record for member: " + memberId);
    }
}
