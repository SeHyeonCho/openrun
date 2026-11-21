package com.openrun.reservation.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShowtimeTest {

    private static final Instant SALES_OPEN_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant STARTS_AT = SALES_OPEN_AT.plus(Duration.ofDays(7));
    private static final Instant ENDS_AT = STARTS_AT.plus(Duration.ofHours(2));

    private final Performance performance = Performance.create("회차 테스트 공연", "테스트 공연장");

    @Test
    @DisplayName("판매 마감 시각을 정하지 않으면 공연 종료 시각까지 판다")
    void salesCloseDefaultsToEndsAt() {
        Showtime showtime = Showtime.create(performance, SeatType.RESERVED, STARTS_AT, ENDS_AT, SALES_OPEN_AT, 10, 4);

        assertThat(showtime.getSalesCloseAt()).isEqualTo(ENDS_AT);
    }

    @Test
    @DisplayName("판매 마감 시각은 판매 시작 뒤부터 공연 종료 시각까지 정할 수 있다")
    void acceptsSalesCloseBetweenOpenAndEnd() {
        Instant beforeStart = STARTS_AT.minus(Duration.ofHours(1));

        assertThat(create(beforeStart).getSalesCloseAt()).isEqualTo(beforeStart);
        assertThat(create(ENDS_AT).getSalesCloseAt()).isEqualTo(ENDS_AT);
    }

    @Test
    @DisplayName("판매 마감 시각이 판매 시작 시각과 같거나 앞서면 회차를 만들 수 없다")
    void rejectsSalesCloseNotAfterOpen() {
        assertThatThrownBy(() -> create(SALES_OPEN_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(SALES_OPEN_AT.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판매 마감 시각이 공연 종료 시각보다 늦으면 회차를 만들 수 없다")
    void rejectsSalesCloseAfterEnd() {
        assertThatThrownBy(() -> create(ENDS_AT.plusNanos(1_000))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("판매 마감 시각 정각부터 판매가 끝난 것으로 본다")
    void salesClosedFromSalesCloseAt() {
        Showtime showtime = create(ENDS_AT);

        assertThat(showtime.isSalesClosedAt(ENDS_AT.minusNanos(1_000))).isFalse();
        assertThat(showtime.isSalesClosedAt(ENDS_AT)).isTrue();
    }

    private Showtime create(Instant salesCloseAt) {
        return Showtime.create(performance, SeatType.RESERVED, STARTS_AT, ENDS_AT, SALES_OPEN_AT, salesCloseAt, 10, 4);
    }
}
