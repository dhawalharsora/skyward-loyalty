package com.skyward.experience.legacy.client;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

/** Client-side binding for the legacy {@code GetMemberTierResponse} SOAP element. */
@XmlRootElement(name = "GetMemberTierResponse")
@XmlAccessorType(XmlAccessType.FIELD)
public class GetMemberTierResponse {

    @XmlElement(required = true)
    private String memberId;

    @XmlElement(required = true)
    private String tier;

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }
}
