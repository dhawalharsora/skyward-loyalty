package com.skyward.experience.legacy.client;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Client-side binding for the legacy {@code GetMemberTierRequest} SOAP element. */
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
