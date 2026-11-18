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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, MutableClockConfiguration.class})
class OrderCancelExpiryTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant HELD_UNTIL = OPENED_AT.plus(Duration.ofMinutes(10));
    private static final Instant JUST_BEFORE_HELD_UNTIL = HELD_UNTIL.minus(1, ChronoUnit.MICROS);

    @Autowired
    private OrderService orderService;

    @Autowired
    private HoldService holdService;

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
    @DisplayName("선점 기한 직전에는 취소할 수 있고 좌석은 주문 연결과 기한 없이 판매 가능으로 저장된다")
    void cancelsJustBeforeHeldUntil() {
        Showtime showtime = createShowtime();
        Long seatId = saveSeat(showtime, "A1");
        String token = holdAtOpening(showtime, seatId);

        clock.setInstant(JUST_BEFORE_HELD_UNTIL);
        OrderResponse cancelled = orderService.cancel(1L, token);

        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");
        assertThat(orderStatus(token)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForMap("SELECT status, current_order_id, held_until FROM seat WHERE id = ?", seatId))
                .containsEntry("status", "AVAILABLE")
                .containsEntry("current_order_id", null)
                .containsEntry("held_until", null);
    }

    @Test
    @DisplayName("선점 기한 시각부터는 취소를 ORDER_EXPIRED로 거부하고 주문과 좌석은 선점 상태로 남는다")
    void rejectsCancelFromHeldUntil() {
        Showtime showtime = createShowtime();
        Long seatId = saveSeat(showtime, "A1");
        String token = holdAtOpening(showtime, seatId);

        clock.setInstant(HELD_UNTIL);

        assertThatThrownBy(() -> orderService.cancel(1L, token))
                .isInstanceOfSatisfying(OrderRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(OrderRejection.ORDER_EXPIRED));
        assertThat(orderStatus(token)).isEqualTo("HELD");
        assertThat(seatStatus(seatId)).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT current_order_id FROM seat WHERE id = ?", Long.class, seatId))
                .isEqualTo(orderIdOf(token));
    }

    @Test
    @DisplayName("다른 구매자가 다시 선점해 만료된 주문을 취소하면 ORDER_EXPIRED로 거부하고 좌석은 새 주문에 남는다")
    void rejectsCancelOfOrderExpiredByAnotherHold() {
        Showtime showtime = createShowtime();
        Long seatId = saveSeat(showtime, "A1");
        String token = holdAtOpening(showtime, seatId);
        clock.setInstant(HELD_UNTIL);
        String other = holdService.hold(2L, UUID.randomUUID(),
                new CreateOrderRequest(showtime.getId(), List.of(seatId), null, "밤산책")).token();

        assertThatThrownBy(() -> orderService.cancel(1L, token))
                .isInstanceOfSatisfying(OrderRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(OrderRejection.ORDER_EXPIRED));
        assertThat(orderStatus(token)).isEqualTo("EXPIRED");
        assertThat(orderStatus(other)).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT current_order_id FROM seat WHERE id = ?", Long.class, seatId))
                .isEqualTo(orderIdOf(other));
    }

    private Showtime createShowtime() {
        Performance performance = performanceRepository.save(Performance.create("취소 만료 테스트 공연", "테스트 공연장"));
        Instant startsAt = OPENED_AT.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, SeatType.RESERVED,
                startsAt, startsAt.plus(Duration.ofHours(2)),
                OPENED_AT.minus(Duration.ofHours(1)), 10, 4));
    }

    private Long saveSeat(Showtime showtime, String label) {
        return seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, label)).getId();
    }

    private String holdAtOpening(Showtime showtime, Long seatId) {
        clock.setInstant(OPENED_AT);
        return holdService.hold(1L, UUID.randomUUID(),
                new CreateOrderRequest(showtime.getId(), List.of(seatId), null, "밤산책")).token();
    }

    private String orderStatus(String token) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE token = ?", String.class, token);
    }

    private Long orderIdOf(String token) {
        return jdbcTemplate.queryForObject("SELECT id FROM orders WHERE token = ?", Long.class, token);
    }

    private String seatStatus(Long seatId) {
        return jdbcTemplate.queryForObject("SELECT status FROM seat WHERE id = ?", String.class, seatId);
    }
}
