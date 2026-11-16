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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
class GeneralAdmissionHoldConcurrencyTest {

    private static final int SEATS_FOR_SALE = 10;
    private static final int SEATS_NOT_FOR_SALE = 2;
    private static final int BUYERS = 30;

    @Autowired
    private OrderService orderService;

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
    @DisplayName("남은 수보다 많은 수량을 여러 구매자가 동시에 선점해도 판매하는 좌석 수를 넘지 않고, 일부만 잡힌 주문이 없다")
    void concurrentQuantityHoldsNeverOversell() throws Exception {
        Showtime showtime = createGeneralShowtime();
        for (int i = 1; i <= SEATS_FOR_SALE; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        for (int i = 1; i <= SEATS_NOT_FOR_SALE; i++) {
            seatRepository.save(Seat.notForSale(showtime, SeatType.GENERAL, "onsite-" + i));
        }

        List<Attempt> attempts = runConcurrently(showtime);

        List<Attempt> succeeded = attempts.stream().filter(Attempt::succeeded).toList();
        int heldBySuccesses = succeeded.stream().mapToInt(Attempt::quantity).sum();
        assertThat(heldBySuccesses).isPositive().isLessThanOrEqualTo(SEATS_FOR_SALE);
        assertThat(attempts).filteredOn(attempt -> !attempt.succeeded())
                .allMatch(attempt -> attempt.rejection() == SeatHoldRejection.SEAT_NOT_AVAILABLE);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'HELD'")).isEqualTo(heldBySuccesses);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'NOT_FOR_SALE'")).isEqualTo(SEATS_NOT_FOR_SALE);
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(succeeded.size());
        assertThat(seatCountsByBuyer()).isEqualTo(succeeded.stream()
                .collect(Collectors.toMap(Attempt::buyerId, attempt -> (long) attempt.quantity())));
        assertThat(count("""
                SELECT COUNT(*) FROM seat s JOIN orders o ON o.id = s.current_order_id
                WHERE s.status = 'HELD' AND s.held_until = o.held_until
                """)).isEqualTo(heldBySuccesses);
        assertThat(count("""
                SELECT COUNT(*) FROM order_seat os JOIN seat s ON s.id = os.seat_id
                WHERE s.current_order_id IS NULL OR s.current_order_id <> os.order_id
                """)).isZero();
        assertThat(count("""
                SELECT COUNT(*) FROM seat s
                WHERE s.status = 'HELD'
                  AND NOT EXISTS (
                      SELECT 1 FROM order_seat os WHERE os.seat_id = s.id AND os.order_id = s.current_order_id)
                """)).isZero();
    }

    private List<Attempt> runConcurrently(Showtime showtime) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(BUYERS);
        List<Attempt> attempts = new ArrayList<>();
        try {
            CountDownLatch ready = new CountDownLatch(BUYERS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Attempt>> futures = new ArrayList<>();
            for (int i = 0; i < BUYERS; i++) {
                long buyerId = i + 1;
                int quantity = 1 + i % 3;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        holdService.hold(buyerId, UUID.randomUUID(),
                                new CreateOrderRequest(showtime.getId(), null, quantity, "구매자" + buyerId));
                        return new Attempt(buyerId, quantity, true, null);
                    } catch (SeatHoldRejectedException e) {
                        return new Attempt(buyerId, quantity, false, e.getRejection());
                    }
                }));
            }
            ready.await();
            start.countDown();
            for (Future<Attempt> future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        return attempts;
    }

    private Map<Long, Long> seatCountsByBuyer() {
        return jdbcTemplate.query("""
                        SELECT o.buyer_id, COUNT(os.id) AS seats
                        FROM orders o JOIN order_seat os ON os.order_id = o.id
                        GROUP BY o.buyer_id
                        """,
                (rs, row) -> Map.entry(rs.getLong("buyer_id"), rs.getLong("seats")))
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Showtime createGeneralShowtime() {
        Performance performance = performanceRepository.save(Performance.create("자유석 동시성 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, 4));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private record Attempt(long buyerId, int quantity, boolean succeeded, SeatHoldRejection rejection) {
    }
}
