/**
 * JAXB bindings for the legacy tiers SOAP contract. The package-level {@code @XmlSchema} sets the target
 * namespace and qualified element form once, so the individual binding classes don't repeat it. These
 * are deliberately plain mutable beans — they stand in for the classes XJC would generate from the XSD.
 */
@XmlSchema(
        namespace = "http://skyward.com/legacy/tiers",
        elementFormDefault = XmlNsForm.QUALIFIED)
package com.skyward.legacy.tiers;

import jakarta.xml.bind.annotation.XmlNsForm;
import jakarta.xml.bind.annotation.XmlSchema;
