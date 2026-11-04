package com.openrun.reservation;

import com.openrun.reservation.common.config.ClockConfig;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

    @Bean
    MutableClock mutableClock() {
        return new MutableClock(Instant.parse("2026-11-04T10:00:00Z"));
    }

    @Bean
    @Primary
    Clock testClock(MutableClock mutableClock) {
        return ClockConfig.truncatedToDatabasePrecision(mutableClock);
    }
}
