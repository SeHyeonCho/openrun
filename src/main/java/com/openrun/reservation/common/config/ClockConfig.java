package com.openrun.reservation.common.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    private static final Duration DATABASE_PRECISION = Duration.ofNanos(1_000);

    @Bean
    public Clock clock() {
        return truncatedToDatabasePrecision(Clock.systemUTC());
    }

    public static Clock truncatedToDatabasePrecision(Clock source) {
        return Clock.tick(source, DATABASE_PRECISION);
    }
}
