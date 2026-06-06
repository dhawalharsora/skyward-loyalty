package com.skyward.legacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Boot entry point for the standalone <b>legacy SOAP tier service</b> — the "old SOA" the strangler fig
 * gradually retires. It owns its own in-memory data and serves its business contract over SOAP, so it
 * behaves like a genuinely separate legacy system the new platform must front and migrate off. A small
 * out-of-band admin REST endpoint exists only to load data into the store (see {@code LegacySeedController});
 * the SOAP contract itself stays frozen.
 */
@SpringBootApplication
public class LegacyTierApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegacyTierApplication.class, args);
    }
}
