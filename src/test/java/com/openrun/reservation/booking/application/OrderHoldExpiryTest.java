package com.openrun.reservation.booking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openrun.reservation.DatabaseCleaner;
import com.openrun.reservation.MutableClock;
import com.openrun.reservation.MutableClockConfiguration;
import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.SeatType;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, MutableClockConfiguration.class})
class OrderHoldExpiryTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final int HOLD_MINUTES = 10;

    @Autowired
    private OrderService orderService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("선점 기한 전에는 다시 선점할 수 없고 기한 시각부터 다시 선점하면 이전 주문이 만료된다")
    void seatCanBeHeldAgainFromHeldUntil() {
        Showtime showtime = createShowtime();
        Long seatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        clock.setInstant(OPENED_AT);
        OrderResponse first = orderService.hold(1L, new CreateOrderRequest(showtime.getId(), List.of(seatId)));
        Instant heldUntil = OPENED_AT.plus(Duration.ofMinutes(HOLD_MINUTES));

        clock.setInstant(heldUntil.minusNanos(1));
        assertThatThrownBy(() -> orderService.hold(2L, new CreateOrderRequest(showtime.getId(), List.of(seatId))))
                .isInstanceOfSatisfying(SeatHoldRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SEAT_NOT_AVAILABLE));

        clock.setInstant(heldUntil);
        OrderResponse second = orderService.hold(2L, new CreateOrderRequest(showtime.getId(), List.of(seatId)));

        assertThat(orderStatus(first.orderId())).isEqualTo("EXPIRED");
        assertThat(orderStatus(second.orderId())).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT current_order_id FROM seat WHERE id = ?", Long.class, seatId))
                .isEqualTo(second.orderId());
        assertThat(second.heldUntil()).isEqualTo(heldUntil.plus(Duration.ofMinutes(HOLD_MINUTES)));
    }

    @Test
    @DisplayName("원래 구매자도 선점 기한이 지난 자기 좌석을 다시 선점할 수 있다")
    void originalBuyerCanHoldExpiredSeatAgain() {
        Showtime showtime = createShowtime();
        Long seatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        clock.setInstant(OPENED_AT);
        OrderResponse first = orderService.hold(1L, new CreateOrderRequest(showtime.getId(), List.of(seatId)));

        clock.setInstant(OPENED_AT.plus(Duration.ofMinutes(HOLD_MINUTES)));
        OrderResponse second = orderService.hold(1L, new CreateOrderRequest(showtime.getId(), List.of(seatId)));

        assertThat(orderStatus(first.orderId())).isEqualTo("EXPIRED");
        assertThat(orderStatus(second.orderId())).isEqualTo("HELD");
    }

    @Test
    @DisplayName("현재 시각에 마이크로초 미만 값이 있어도 기한 직전의 선점은 1인 최대 좌석 수에 포함한다")
    void heldSeatsCountTowardLimitJustBeforeHeldUntilWithSubMicrosecondClock() {
        Showtime showtime = createShowtime();
        Long heldSeatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        Long otherSeatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A2")).getId();
        clock.setInstant(OPENED_AT);
        orderService.hold(1L, new CreateOrderRequest(showtime.getId(), List.of(heldSeatId)));

        clock.setInstant(OPENED_AT.plus(Duration.ofMinutes(HOLD_MINUTES)).minusNanos(1));

        assertThatThrownBy(() -> orderService.hold(1L, new CreateOrderRequest(showtime.getId(), List.of(otherSeatId))))
                .isInstanceOfSatisfying(SeatHoldRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SEAT_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("확정한 좌석은 선점 기한이 지나도 1인 최대 좌석 수에 포함한다")
    void confirmedSeatsCountTowardLimitAfterHeldUntil() {
        Showtime showtime = createShowtime();
        Long confirmedSeatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        Long otherSeatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A2")).getId();
        clock.setInstant(OPENED_AT);
        OrderResponse confirmed = orderService.hold(1L, new CreateOrderRequest(showtime.getId(), List.of(confirmedSeatId)));
        jdbcTemplate.update("UPDATE orders SET status = 'CONFIRMED' WHERE id = ?", confirmed.orderId());
        jdbcTemplate.update("UPDATE seat SET status = 'SOLD', held_until = NULL WHERE id = ?", confirmedSeatId);

        clock.setInstant(OPENED_AT.plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> orderService.hold(1L, new CreateOrderRequest(showtime.getId(), List.of(otherSeatId))))
                .isInstanceOfSatisfying(SeatHoldRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SEAT_LIMIT_EXCEEDED));
    }

    private Showtime createShowtime() {
        Performance performance = performanceRepository.save(Performance.create("만료 테스트 공연"));
        return showtimeRepository.save(Showtime.create(performance, OPENED_AT.plus(Duration.ofDays(7)),
                OPENED_AT.minus(Duration.ofHours(1)), HOLD_MINUTES, 1));
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }
}
