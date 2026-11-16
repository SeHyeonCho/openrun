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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, MutableClockConfiguration.class})
class GeneralAdmissionHoldExpiryTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final int HOLD_MINUTES = 10;
    private static final Instant HELD_UNTIL = OPENED_AT.plus(Duration.ofMinutes(HOLD_MINUTES));

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
    @DisplayName("기한 전에는 선점된 자유석을 다시 잡을 수 없고, 기한 시각부터 다시 선점하면 이전 주문이 만료되고 좌석은 새 주문에 속한다")
    void expiredGeneralSeatCanBeHeldAgainFromHeldUntil() {
        Showtime showtime = createGeneralShowtime();
        Long seatId = seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, "1")).getId();
        clock.setInstant(OPENED_AT);
        OrderResponse first = holdOne(showtime, 1L);

        clock.setInstant(HELD_UNTIL.minusNanos(1));
        assertThatThrownBy(() -> holdOne(showtime, 2L))
                .isInstanceOfSatisfying(SeatHoldRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SEAT_NOT_AVAILABLE));

        clock.setInstant(HELD_UNTIL);
        OrderResponse second = holdOne(showtime, 2L);

        assertThat(orderStatus(first.token())).isEqualTo("EXPIRED");
        assertThat(orderStatus(second.token())).isEqualTo("HELD");
        assertThat(jdbcTemplate.queryForObject("SELECT current_order_id FROM seat WHERE id = ?", Long.class, seatId))
                .isEqualTo(jdbcTemplate.queryForObject("SELECT id FROM orders WHERE token = ?", Long.class,
                        second.token()));
        assertThat(second.heldUntil()).isEqualTo(HELD_UNTIL.plus(Duration.ofMinutes(HOLD_MINUTES)));
    }

    private OrderResponse holdOne(Showtime showtime, Long buyerId) {
        return orderService.hold(buyerId, new CreateOrderRequest(showtime.getId(), null, 1, "구매자" + buyerId));
    }

    private Showtime createGeneralShowtime() {
        Performance performance = performanceRepository.save(Performance.create("자유석 만료 테스트 공연", "테스트 공연장"));
        Instant startsAt = OPENED_AT.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL,
                startsAt, startsAt.plus(Duration.ofHours(2)), OPENED_AT.minus(Duration.ofHours(1)), HOLD_MINUTES, 4));
    }

    private String orderStatus(String token) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE token = ?", String.class, token);
    }
}
