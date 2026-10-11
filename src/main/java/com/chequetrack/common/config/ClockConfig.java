package com.chequetrack.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Single source of "now". Code asks this clock instead of calling Instant.now()
 * so rules that depend on time (lockout, expiry, due dates) can be tested.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.business-time-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
