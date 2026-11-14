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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
class OrderHoldConcurrencyTest {

    private static final int THREADS = 50;

    @Autowired
    private OrderService orderService;

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
    @DisplayName("같은 좌석에 여러 사용자가 동시에 선점하면 정확히 한 명만 성공한다")
    void onlyOneOrderHoldsContendedSeat() throws Exception {
        Showtime showtime = createShowtime(4);
        Seat seat = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1"));

        List<Outcome> outcomes = runConcurrently(THREADS,
                i -> new HoldAttempt((long) i, new CreateOrderRequest(showtime.getId(), List.of(seat.getId()), "밤산책")));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allMatch(outcome -> outcome.rejection() == SeatHoldRejection.SEAT_NOT_AVAILABLE);
        assertThat(heldSeatCount(showtime)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM seat s
                JOIN orders o ON o.id = s.current_order_id
                WHERE s.id = %d
                  AND s.held_until IS NOT NULL
                  AND s.held_until = o.held_until
                  AND s.held_until > NOW(6)
                """.formatted(seat.getId()))).isEqualTo(1);
    }

    @Test
    @DisplayName("겹치는 좌석을 서로 다른 순서로 동시에 요청해도 일부만 선점된 주문과 교착이 생기지 않는다")
    void overlappingSeatsInDifferentOrderDoNotPartiallyHoldOrDeadlock() throws Exception {
        Showtime showtime = createShowtime(4);
        Long first = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        Long second = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A2")).getId();

        List<Outcome> outcomes = runConcurrently(THREADS, i -> new HoldAttempt((long) i, new CreateOrderRequest(
                showtime.getId(), i % 2 == 0 ? List.of(first, second) : List.of(second, first), "밤산책")));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allMatch(outcome -> outcome.rejection() == SeatHoldRejection.SEAT_NOT_AVAILABLE);
        assertThat(heldSeatCount(showtime)).isEqualTo(2);
        assertThat(count("SELECT COUNT(DISTINCT current_order_id) FROM seat WHERE status = 'HELD'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(2);
    }

    @Test
    @DisplayName("선점 기한이 지난 좌석에 여러 사용자가 동시에 다시 선점하면 정확히 한 명만 성공한다")
    void onlyOneOrderHoldsExpiredSeatAgain() throws Exception {
        Showtime showtime = createShowtime(4);
        Seat seat = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1"));
        String expiredToken = orderService.hold(0L, new CreateOrderRequest(showtime.getId(), List.of(seat.getId()), "밤산책"))
                .token();
        jdbcTemplate.update("UPDATE orders SET held_until = NOW(6) - INTERVAL 1 MINUTE WHERE token = ?", expiredToken);
        jdbcTemplate.update("UPDATE seat SET held_until = NOW(6) - INTERVAL 1 MINUTE WHERE id = ?", seat.getId());

        List<Outcome> outcomes = runConcurrently(THREADS,
                i -> new HoldAttempt((long) i + 1, new CreateOrderRequest(showtime.getId(), List.of(seat.getId()), "밤산책")));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allMatch(outcome -> outcome.rejection() == SeatHoldRejection.SEAT_NOT_AVAILABLE);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE token = ?", String.class, expiredToken))
                .isEqualTo("EXPIRED");
        assertThat(count("SELECT COUNT(*) FROM orders WHERE status = 'HELD'")).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM seat s
                JOIN orders o ON o.id = s.current_order_id
                WHERE s.id = %d AND o.status = 'HELD' AND s.held_until = o.held_until AND s.held_until > NOW(6)
                """.formatted(seat.getId()))).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사용자가 동시에 선점해도 1인 최대 좌석 수를 넘지 않는다")
    void sameUserCannotExceedSeatLimitConcurrently() throws Exception {
        int maxSeatsPerUser = 4;
        int requests = 10;
        Showtime showtime = createShowtime(maxSeatsPerUser);
        List<Long> seatIds = new ArrayList<>();
        for (int i = 1; i <= requests; i++) {
            seatIds.add(seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A" + i)).getId());
        }

        List<Outcome> outcomes = runConcurrently(requests,
                i -> new HoldAttempt(1L, new CreateOrderRequest(showtime.getId(), List.of(seatIds.get(i)), "밤산책")));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(maxSeatsPerUser);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .allMatch(outcome -> outcome.rejection() == SeatHoldRejection.SEAT_LIMIT_EXCEEDED);
        assertThat(heldSeatCount(showtime)).isEqualTo(maxSeatsPerUser);
    }

    private List<Outcome> runConcurrently(int threads, IntFunction<HoldAttempt> attemptFor) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            HoldAttempt attempt = attemptFor.apply(i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    orderService.hold(attempt.buyerId(), attempt.request());
                    return Outcome.success();
                } catch (SeatHoldRejectedException e) {
                    return Outcome.rejected(e.getRejection());
                }
            }));
        }
        ready.await();
        start.countDown();
        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return outcomes;
    }

    private Showtime createShowtime(int maxSeatsPerUser) {
        Performance performance = performanceRepository.save(Performance.create("동시성 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, startsAt, startsAt.plus(Duration.ofHours(2)),
                now.minus(Duration.ofHours(1)), 10, maxSeatsPerUser));
    }

    private int heldSeatCount(Showtime showtime) {
        return count("SELECT COUNT(*) FROM seat WHERE status = 'HELD' AND showtime_id = " + showtime.getId());
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private record HoldAttempt(Long buyerId, CreateOrderRequest request) {
    }

    private record Outcome(boolean succeeded, SeatHoldRejection rejection) {

        static Outcome success() {
            return new Outcome(true, null);
        }

        static Outcome rejected(SeatHoldRejection rejection) {
            return new Outcome(false, rejection);
        }
    }
}
