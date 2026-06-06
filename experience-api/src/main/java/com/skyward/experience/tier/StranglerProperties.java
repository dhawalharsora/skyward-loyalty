package com.skyward.experience.tier;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config-driven strangler routing. {@code legacyPercent} is the share of the member population served by
 * the legacy path (0 = fully cut over to new, 100 = all traffic still on legacy); the rest go to the new
 * domain service. The two URIs point the facade at each backend. All of this is config so the cutover can
 * be dialled at runtime/per-environment with no code change — the essence of a controlled migration.
 */
@ConfigurationProperties(prefix = "skyward.strangler")
public class StranglerProperties {

    /** Percentage (0–100) of members routed to the legacy SOAP service. */
    private int legacyPercent = 0;

    /** Base URL of the new domain (REST) tier service. */
    private String domainBaseUrl = "http://localhost:8080";

    /** Endpoint URI of the legacy SOAP tier service. */
    private String legacyUri = "http://localhost:8081/ws";

    /** Shadow-compare settings (the pre-cutover phase). */
    private final Shadow shadow = new Shadow();

    public int getLegacyPercent() {
        return legacyPercent;
    }

    public void setLegacyPercent(int legacyPercent) {
        this.legacyPercent = legacyPercent;
    }

    public String getDomainBaseUrl() {
        return domainBaseUrl;
    }

    public void setDomainBaseUrl(String domainBaseUrl) {
        this.domainBaseUrl = domainBaseUrl;
    }

    public String getLegacyUri() {
        return legacyUri;
    }

    public void setLegacyUri(String legacyUri) {
        this.legacyUri = legacyUri;
    }

    public Shadow getShadow() {
        return shadow;
    }

    /**
     * Shadow-compare configuration. When {@code enabled}, the facade calls both paths, serves the
     * {@code authoritative} one, and reports mismatches — superseding percentage routing. The two
     * stages of a migration: shadow first (compare, serve old), then percentage cutover (shift cohorts).
     * {@code authoritative} is normally "legacy" pre-cutover; flip to "domain" to shadow legacy and
     * confirm parity before decommissioning it.
     */
    public static class Shadow {

        private boolean enabled = false;
        private String authoritative = "legacy";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAuthoritative() {
            return authoritative;
        }

        public void setAuthoritative(String authoritative) {
            this.authoritative = authoritative;
        }
    }
}
