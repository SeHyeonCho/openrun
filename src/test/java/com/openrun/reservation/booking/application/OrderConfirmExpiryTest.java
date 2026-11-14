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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, MutableClockConfiguration.class})
class OrderConfirmExpiryTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant HELD_UNTIL = OPENED_AT.plus(Duration.ofMinutes(10));
    private static final Instant JUST_BEFORE_HELD_UNTIL = HELD_UNTIL.minus(1, ChronoUnit.MICROS);

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
    @DisplayName("선점 기한 직전에는 확정할 수 있고 확정한 좌석은 판매 완료로 저장된다")
    void confirmsJustBeforeHeldUntil() {
        Showtime showtime = createShowtime();
        Long seatId = saveSeat(showtime, "A1");
        String token = holdAtOpening(showtime, seatId);

        clock.setInstant(JUST_BEFORE_HELD_UNTIL);
        OrderResponse confirmed = orderService.confirm(1L, token);

        assertThat(confirmed.status().name()).isEqualTo("CONFIRMED");
        assertThat(orderStatus(token)).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForMap("SELECT status, current_order_id, held_until FROM seat WHERE id = ?", seatId))
                .containsEntry("status", "SOLD")
                .containsEntry("current_order_id", orderIdOf(token))
                .containsEntry("held_until", null);
    }

    @Test
    @DisplayName("선점 기한 시각부터는 확정을 ORDER_EXPIRED로 거부하고 주문과 좌석은 선점 상태로 남는다")
    void rejectsConfirmFromHeldUntil() {
        Showtime showtime = createShowtime();
        Long seatId = saveSeat(showtime, "A1");
        String token = holdAtOpening(showtime, seatId);

        clock.setInstant(HELD_UNTIL);

        assertThatThrownBy(() -> orderService.confirm(1L, token))
                .isInstanceOfSatisfying(OrderConfirmRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(OrderConfirmRejection.ORDER_EXPIRED));
        assertThat(orderStatus(token)).isEqualTo("HELD");
        assertThat(seatStatus(seatId)).isEqualTo("HELD");
    }

    @Test
    @DisplayName("확정한 좌석은 선점 기한이 지나도 다른 구매자가 선점할 수 없다")
    void confirmedSeatCannotBeHeldAfterHeldUntil() {
        Showtime showtime = createShowtime();
        Long seatId = saveSeat(showtime, "A1");
        String token = holdAtOpening(showtime, seatId);
        clock.setInstant(JUST_BEFORE_HELD_UNTIL);
        orderService.confirm(1L, token);

        clock.setInstant(HELD_UNTIL.plus(Duration.ofDays(1)));

        assertThatThrownBy(() -> orderService.hold(2L, new CreateOrderRequest(showtime.getId(), List.of(seatId), "밤산책")))
                .isInstanceOfSatisfying(SeatHoldRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SEAT_NOT_AVAILABLE));
        assertThat(orderStatus(token)).isEqualTo("CONFIRMED");
        assertThat(seatStatus(seatId)).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("확정한 주문은 선점 기한이 지난 뒤 다시 확정해도 같은 결과를 반환한다")
    void confirmingAgainAfterHeldUntilReturnsSameResult() {
        Showtime showtime = createShowtime();
        Long seatId = saveSeat(showtime, "A1");
        String token = holdAtOpening(showtime, seatId);
        clock.setInstant(JUST_BEFORE_HELD_UNTIL);
        OrderResponse first = orderService.confirm(1L, token);

        clock.setInstant(HELD_UNTIL.plus(Duration.ofDays(1)));
        OrderResponse second = orderService.confirm(1L, token);

        assertThat(second).usingRecursiveComparison().ignoringFields("serverTime").isEqualTo(first);
        assertThat(seatStatus(seatId)).isEqualTo("SOLD");
    }

    private Showtime createShowtime() {
        Performance performance = performanceRepository.save(Performance.create("확정 만료 테스트 공연", "테스트 공연장"));
        Instant startsAt = OPENED_AT.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, startsAt, startsAt.plus(Duration.ofHours(2)),
                OPENED_AT.minus(Duration.ofHours(1)), 10, 4));
    }

    private Long saveSeat(Showtime showtime, String label) {
        return seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, label)).getId();
    }

    private String holdAtOpening(Showtime showtime, Long seatId) {
        clock.setInstant(OPENED_AT);
        return orderService.hold(1L, new CreateOrderRequest(showtime.getId(), List.of(seatId), "밤산책")).token();
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
