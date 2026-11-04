package com.openrun.reservation.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClockConfigTest {

    @Test
    @DisplayName("애플리케이션 시계는 DB가 저장하지 못하는 마이크로초 미만 값을 버린다")
    void truncatesBelowMicrosecond() {
        Clock source = Clock.fixed(Instant.parse("2026-11-04T10:09:59.999999999Z"), ZoneOffset.UTC);

        Instant now = ClockConfig.truncatedToDatabasePrecision(source).instant();

        assertThat(now).isEqualTo(Instant.parse("2026-11-04T10:09:59.999999Z"));
    }
}
