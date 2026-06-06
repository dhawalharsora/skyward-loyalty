package com.skyward.legacy.tiers;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Inbound SOAP request: look up the legacy tier for a member id. Mirrors the XSD element. */
@XmlRootElement(name = "GetMemberTierRequest")
@XmlAccessorType(XmlAccessType.FIELD)
public class GetMemberTierRequest {

    @XmlElement(required = true)
    private String memberId;

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }
}
