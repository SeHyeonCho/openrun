package com.openrun.reservation.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeatHoldTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private final Showtime showtime = Showtime.create(Performance.create("도메인 테스트 공연"),
            NOW.plus(Duration.ofDays(7)), NOW.minus(Duration.ofHours(1)), 10, 4);

    @Test
    @DisplayName("판매 시작 시각부터 판매가 열린 것으로 본다")
    void salesOpenFromSalesOpenAt() {
        assertThat(showtime.isSalesOpenAt(showtime.getSalesOpenAt())).isTrue();
        assertThat(showtime.isSalesOpenAt(showtime.getSalesOpenAt().minusNanos(1))).isFalse();
    }

    @Test
    @DisplayName("주문을 만들면 회차의 선점 유지 시간만큼 뒤까지 선점한다")
    void orderHoldsUntilHoldDeadline() {
        Order order = Order.hold(showtime, 1L, NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.HELD);
        assertThat(order.getHeldUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
    }

    @Test
    @DisplayName("선점 가능한 좌석을 선점하면 주문과 선점 기한을 기록한다")
    void holdsAvailableSeat() {
        Seat seat = Seat.forSale(showtime, SeatType.RESERVED, "A1");
        Order order = Order.hold(showtime, 1L, NOW);

        seat.hold(order);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(seat.getCurrentOrder()).isSameAs(order);
        assertThat(seat.getHeldUntil()).isEqualTo(order.getHeldUntil());
    }

    @Test
    @DisplayName("판매하지 않는 좌석은 선점할 수 없다")
    void rejectsSeatNotForSale() {
        Seat seat = Seat.notForSale(showtime, SeatType.RESERVED, "A1");

        assertThatThrownBy(() -> seat.hold(Order.hold(showtime, 1L, NOW)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(seat.getStatus()).isEqualTo(SeatStatus.NOT_FOR_SALE);
    }

    @Test
    @DisplayName("이미 선점된 좌석은 다른 주문이 선점할 수 없다")
    void rejectsSeatAlreadyHeld() {
        Seat seat = Seat.forSale(showtime, SeatType.RESERVED, "A1");
        Order first = Order.hold(showtime, 1L, NOW);
        seat.hold(first);

        assertThatThrownBy(() -> seat.hold(Order.hold(showtime, 2L, NOW)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(seat.getCurrentOrder()).isSameAs(first);
    }
}
