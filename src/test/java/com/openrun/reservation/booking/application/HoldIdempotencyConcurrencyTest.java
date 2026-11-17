package com.openrun.reservation.booking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openrun.reservation.DatabaseCleaner;
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
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
class HoldIdempotencyConcurrencyTest {

    private static final int REQUESTS = 20;

    @Autowired
    private HoldService holdService;

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
    @DisplayName("같은 식별자, 같은 내용의 선점이 동시에 와도 주문은 하나만 생기고 모든 요청이 같은 주문을 받는다")
    void concurrentSameRequestsCreateOneOrder() throws Exception {
        Showtime showtime = createGeneralShowtime(10);
        UUID key = UUID.randomUUID();

        List<Callable<Result>> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            tasks.add(() -> attempt(key, showtime, 2));
        }
        List<Result> results = runConcurrently(tasks);

        assertThat(results).allMatch(result -> result.token() != null);
        assertThat(results).extracting(Result::token).containsOnly(results.getFirst().token());
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hold_request")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'HELD'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(2);
        assertThat(heldSeatsOwnedBy(results.getFirst().token())).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 식별자를 다른 회차로 동시에 보내도 주문은 하나만 생기고, 다른 내용의 요청은 422이며 그 회차의 좌석은 바뀌지 않는다")
    void concurrentSameKeyForDifferentShowtimesCreateOneOrder() throws Exception {
        Showtime first = createGeneralShowtime(10);
        Showtime second = createGeneralShowtime(10);
        UUID key = UUID.randomUUID();

        List<Callable<Result>> tasks = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            Showtime showtime = i % 2 == 0 ? first : second;
            tasks.add(() -> attempt(key, showtime, 2));
        }
        List<Result> results = runConcurrently(tasks);

        List<Result> succeeded = results.stream().filter(result -> result.token() != null).toList();
        assertThat(succeeded).isNotEmpty();
        assertThat(succeeded).extracting(Result::token).containsOnly(succeeded.getFirst().token());
        Long winner = succeeded.getFirst().showtimeId();
        assertThat(succeeded).allMatch(result -> result.showtimeId().equals(winner));
        assertThat(results).filteredOn(result -> result.token() == null)
                .allMatch(result -> result.reused() && !result.showtimeId().equals(winner));
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM hold_request")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'HELD' AND showtime_id = " + winner)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(2);
        assertThat(heldSeatsOwnedBy(succeeded.getFirst().token())).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status <> 'AVAILABLE' AND showtime_id <> " + winner))
                .isZero();
    }

    private Result attempt(UUID key, Showtime showtime, int quantity) {
        try {
            OrderResponse response = holdService.hold(1L, key,
                    new CreateOrderRequest(showtime.getId(), null, quantity, "밤산책"));
            return new Result(showtime.getId(), response.token(), false);
        } catch (IdempotencyKeyReusedException e) {
            return new Result(showtime.getId(), null, true);
        }
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

    private Showtime createGeneralShowtime(int seats) {
        Performance performance = performanceRepository.save(Performance.create("식별자 동시성 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, 4));
        for (int i = 1; i <= seats; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        return showtime;
    }

    private int heldSeatsOwnedBy(String token) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM seat s
                JOIN orders o ON o.id = s.current_order_id
                JOIN order_seat os ON os.order_id = o.id AND os.seat_id = s.id
                WHERE o.token = ? AND s.status = 'HELD'
                """, Integer.class, token);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private record Result(Long showtimeId, String token, boolean reused) {
    }
}
