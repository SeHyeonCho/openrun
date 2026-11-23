package com.openrun.reservation.booking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openrun.reservation.DatabaseCleaner;
import com.openrun.reservation.MutableClock;
import com.openrun.reservation.MutableClockConfiguration;
import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.booking.application.PerformanceResponse.ShowtimeSummary;
import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.SalesStatus;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.SeatType;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class PerformanceQueryTest {

    private static final Instant SALES_OPEN_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant STARTS_AT = SALES_OPEN_AT.plus(Duration.ofDays(7));
    private static final Instant SALES_CLOSE_AT = STARTS_AT.minus(Duration.ofHours(1));
    private static final int HOLD_MINUTES = 10;

    @Autowired
    private PerformanceQueryService performanceQueryService;

    @Autowired
    private HoldService holdService;

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
    @DisplayName("남은 수는 확정·기한 안 선점·판매 제외 좌석을 빼고, 저장 상태가 HELD여도 기한이 지난 선점과 취소한 좌석은 센다")
    void remainingCountsByRule() {
        Performance performance = savePerformance();
        Showtime showtime = saveGeneralShowtime(performance, STARTS_AT, 6);
        seatRepository.save(Seat.notForSale(showtime, SeatType.GENERAL, "현장"));

        clock.setInstant(SALES_OPEN_AT);
        holdOne(showtime, 1L);
        clock.setInstant(SALES_OPEN_AT.plus(Duration.ofMinutes(1)));
        orderService.confirm(2L, holdOne(showtime, 2L));
        holdOne(showtime, 3L);
        orderService.cancel(4L, holdOne(showtime, 4L));
        clock.setInstant(SALES_OPEN_AT.plus(Duration.ofMinutes(HOLD_MINUTES)));

        ShowtimeSummary summary = onlyShowtime(performance);

        assertThat(countSeats(showtime, "HELD")).isEqualTo(2);
        assertThat(summary.remaining()).isEqualTo(4);
        assertThat(summary.salesStatus()).isEqualTo(SalesStatus.ON_SALE);
    }

    @Test
    @DisplayName("판매 상태는 판매 시작 직전·시작·마감 직전·마감 시각에서 바뀌고, 판매 중 남은 수가 0이면 SOLD_OUT이다")
    void salesStatusChangesAtBoundaries() {
        Performance performance = savePerformance();
        Showtime showtime = saveGeneralShowtime(performance, STARTS_AT, 1);

        assertThat(statusAt(performance, SALES_OPEN_AT.minus(1, ChronoUnit.MICROS))).isEqualTo(SalesStatus.BEFORE_SALE);
        assertThat(statusAt(performance, SALES_OPEN_AT)).isEqualTo(SalesStatus.ON_SALE);
        holdOne(showtime, 1L);
        assertThat(statusAt(performance, SALES_OPEN_AT)).isEqualTo(SalesStatus.SOLD_OUT);
        assertThat(statusAt(performance, SALES_CLOSE_AT.minus(1, ChronoUnit.MICROS))).isEqualTo(SalesStatus.ON_SALE);
        assertThat(statusAt(performance, SALES_CLOSE_AT)).isEqualTo(SalesStatus.SALES_CLOSED);
    }

    @Test
    @DisplayName("공연의 회차는 공연 시작 시각 순으로 주고, 회차마다 남은 수를 따로 센다")
    void showtimesAreOrderedByStartAndCountedSeparately() {
        Performance performance = savePerformance();
        Showtime later = saveGeneralShowtime(performance, STARTS_AT.plus(Duration.ofDays(1)), 3);
        Showtime earlier = saveGeneralShowtime(performance, STARTS_AT, 2);
        clock.setInstant(SALES_OPEN_AT);
        holdOne(later, 1L);

        PerformanceResponse response = performanceQueryService.find(performance.getId());

        assertThat(response.showtimes()).extracting(ShowtimeSummary::id).containsExactly(earlier.getId(), later.getId());
        assertThat(response.showtimes()).extracting(ShowtimeSummary::remaining).containsExactly(2L, 2L);
        assertThat(response.serverTime()).isEqualTo(SALES_OPEN_AT);
    }

    private int countSeats(Showtime showtime, String status) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM seat WHERE showtime_id = ? AND status = ?",
                Integer.class, showtime.getId(), status);
    }

    private SalesStatus statusAt(Performance performance, Instant now) {
        clock.setInstant(now);
        return onlyShowtime(performance).salesStatus();
    }

    private ShowtimeSummary onlyShowtime(Performance performance) {
        return performanceQueryService.find(performance.getId()).showtimes().getFirst();
    }

    private String holdOne(Showtime showtime, Long buyerId) {
        return holdService.hold(buyerId, UUID.randomUUID(),
                new CreateOrderRequest(showtime.getId(), null, 1, "구매자" + buyerId)).token();
    }

    private Performance savePerformance() {
        return performanceRepository.save(Performance.create("공연 페이지 테스트 공연", "테스트 공연장"));
    }

    private Showtime saveGeneralShowtime(Performance performance, Instant startsAt, int seats) {
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL, startsAt,
                startsAt.plus(Duration.ofHours(2)), SALES_OPEN_AT, startsAt.minus(Duration.ofHours(1)),
                HOLD_MINUTES, 4));
        for (int i = 1; i <= seats; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        return showtime;
    }
}
