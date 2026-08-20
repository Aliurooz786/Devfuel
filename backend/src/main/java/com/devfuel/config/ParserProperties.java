package com.devfuel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.parser")
public class ParserProperties {

    /**
     * Stored on every event_logs row (e.g. v1).
     */
    private String version = "v1";

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
