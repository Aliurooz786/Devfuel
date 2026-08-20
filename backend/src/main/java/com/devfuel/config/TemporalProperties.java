package com.devfuel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.temporal")
public class TemporalProperties {

    /**
     * IANA zone for calendar-day resolution (single-user Phase 1).
     */
    private String zone = "Asia/Kolkata";

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }
}
