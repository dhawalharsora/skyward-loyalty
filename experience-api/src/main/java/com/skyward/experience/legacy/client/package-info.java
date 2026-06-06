/**
 * The edge's <b>own</b> SOAP client bindings for the legacy tiers contract.
 *
 * <p>These mirror the legacy service's XSD but are deliberately a separate copy: a strangler consumer
 * generates its client from the published WSDL, not by sharing the legacy server's classes. That keeps
 * the Experience layer coupled to the <em>contract</em>, not to the legacy deployable — the correct
 * boundary for a system you intend to retire. The duplication is across a service boundary, by design.
 */
@XmlSchema(
        namespace = "http://skyward.com/legacy/tiers",
        elementFormDefault = XmlNsForm.QUALIFIED)
package com.skyward.experience.legacy.client;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
