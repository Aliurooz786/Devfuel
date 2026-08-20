package com.devfuel.config;

import com.devfuel.temporal.TemporalResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(TemporalProperties.class)
public class TemporalConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    public TemporalResolver temporalResolver(TemporalProperties properties) {
        return new TemporalResolver(ZoneId.of(properties.getZone()));
    }
}
