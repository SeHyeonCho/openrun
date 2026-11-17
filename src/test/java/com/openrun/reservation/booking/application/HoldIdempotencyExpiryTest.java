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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    private static final int CONCURRENT_REQUESTS = 10;

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

    @Test
    @DisplayName("24시간이 지난 식별자로 같은 내용의 선점이 동시에 와도 새 주문은 하나만 생기고 모든 요청이 그 주문을 받는다")
    void concurrentRequestsAfterExpiryCreateOneNewOrder() throws Exception {
        Showtime showtime = createGeneralShowtime();
        UUID key = UUID.randomUUID();
        clock.setInstant(RECORDED_AT);
        OrderResponse first = holdService.hold(1L, key, holdOf(showtime, 1));
        clock.setInstant(FORGOTTEN_AT);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            tasks.add(() -> holdService.hold(1L, key, holdOf(showtime, 2)).token());
        }
        List<String> tokens = runConcurrently(tasks);

        assertThat(tokens).containsOnly(tokens.getFirst());
        assertThat(tokens.getFirst()).isNotEqualTo(first.token());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM hold_request", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM seat s
                JOIN orders o ON o.id = s.current_order_id
                JOIN order_seat os ON os.order_id = o.id AND os.seat_id = s.id
                WHERE o.token = ? AND s.status = 'HELD'
                """, Integer.class, tokens.getFirst())).isEqualTo(2);
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        List<T> results = new ArrayList<>();
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            ready.await();
            start.countDown();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        return results;
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
