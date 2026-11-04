package com.openrun.reservation.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SeatConfirmTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:00:00Z");

    private final Showtime showtime = Showtime.create(Performance.create("도메인 테스트 공연"),
            NOW.plus(Duration.ofDays(7)), NOW.minus(Duration.ofHours(1)), 10, 4);

    @Test
    @DisplayName("선점 기한 전에는 주문을 확정할 수 있고 기한 시각부터는 확정할 수 없다")
    void orderConfirmableBeforeHeldUntil() {
        Order order = Order.hold(showtime, 1L, NOW);
        Instant heldUntil = order.getHeldUntil();

        assertThat(order.isConfirmableAt(heldUntil.minusNanos(1))).isTrue();
        assertThat(order.isConfirmableAt(heldUntil)).isFalse();
        assertThatThrownBy(() -> order.confirm(heldUntil)).isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.HELD);

        order.confirm(heldUntil.minusNanos(1));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("만료된 주문은 기한 전이라도 확정할 수 없다")
    void rejectsExpiredOrder() {
        Order order = Order.hold(showtime, 1L, NOW);
        order.expire();

        assertThat(order.isConfirmableAt(NOW)).isFalse();
        assertThatThrownBy(() -> order.confirm(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    @Test
    @DisplayName("확정된 주문은 기한이 지난 뒤 다시 확정하거나 만료해도 확정 상태로 남는다")
    void confirmedOrderStaysConfirmed() {
        Order order = Order.hold(showtime, 1L, NOW);
        order.confirm(NOW);

        order.confirm(order.getHeldUntil().plus(Duration.ofDays(1)));
        order.expire();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("주문이 선점한 좌석을 확정하면 판매 완료가 되고 선점 기한을 지운다")
    void confirmsSeatHeldByOrder() {
        Seat seat = Seat.forSale(showtime, SeatType.RESERVED, "A1");
        Order order = Order.hold(showtime, 1L, NOW);
        seat.hold(order, NOW);

        seat.confirm(order);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(seat.getCurrentOrder()).isSameAs(order);
        assertThat(seat.getHeldUntil()).isNull();
    }

    @Test
    @DisplayName("다른 주문이 선점한 좌석이나 선점되지 않은 좌석은 확정할 수 없다")
    void rejectsSeatNotHeldByOrder() {
        Seat heldByOther = Seat.forSale(showtime, SeatType.RESERVED, "A1");
        Order other = Order.hold(showtime, 2L, NOW);
        heldByOther.hold(other, NOW);
        Seat available = Seat.forSale(showtime, SeatType.RESERVED, "A2");
        Order order = Order.hold(showtime, 1L, NOW);

        assertThatThrownBy(() -> heldByOther.confirm(order)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> available.confirm(order)).isInstanceOf(IllegalStateException.class);
        assertThat(heldByOther.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(available.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("판매 완료된 좌석은 시각과 상관없이 다시 선점할 수 없다")
    void soldSeatNeverAvailable() {
        Seat seat = Seat.forSale(showtime, SeatType.RESERVED, "A1");
        Order order = Order.hold(showtime, 1L, NOW);
        seat.hold(order, NOW);
        seat.confirm(order);
        Instant longAfter = NOW.plus(Duration.ofDays(365));

        assertThat(seat.isAvailableAt(longAfter)).isFalse();
        assertThat(seat.statusAt(longAfter)).isEqualTo(SeatStatus.SOLD);
        assertThatThrownBy(() -> seat.hold(Order.hold(showtime, 2L, longAfter), longAfter))
                .isInstanceOf(IllegalStateException.class);
    }
}
