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
class HoldIdempotencyExpiryTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant FORGOTTEN_AT = RECORDED_AT.plus(Duration.ofHours(24));

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
    @DisplayName("기록한 지 24시간 전까지는 같은 식별자의 다른 내용을 422로 거부하고, 24시간이 되면 처음 보는 식별자로 새로 처리한다")
    void forgetsRecordAfterTwentyFourHours() {
        Showtime showtime = createGeneralShowtime();
        UUID key = UUID.randomUUID();
        clock.setInstant(RECORDED_AT);
        OrderResponse first = holdService.hold(1L, key, holdOf(showtime, 1));

        clock.setInstant(FORGOTTEN_AT.minusNanos(1_000));
        assertThatThrownBy(() -> holdService.hold(1L, key, holdOf(showtime, 2)))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(holdService.hold(1L, key, holdOf(showtime, 1)).token()).isEqualTo(first.token());

        clock.setInstant(FORGOTTEN_AT);
        OrderResponse second = holdService.hold(1L, key, holdOf(showtime, 2));

        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(second.quantity()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM hold_request", Integer.class)).isEqualTo(1);
    }

    private CreateOrderRequest holdOf(Showtime showtime, int quantity) {
        return new CreateOrderRequest(showtime.getId(), null, quantity, "밤산책");
    }

    private Showtime createGeneralShowtime() {
        Performance performance = performanceRepository.save(Performance.create("식별자 기억 기간 테스트 공연", "테스트 공연장"));
        Instant startsAt = RECORDED_AT.plus(Duration.ofDays(7));
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL,
                startsAt, startsAt.plus(Duration.ofHours(2)), RECORDED_AT.minus(Duration.ofHours(1)), 10, 4));
        for (int i = 1; i <= 5; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        return showtime;
    }
}
