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
class OrderCancelConcurrencyTest {

    private static final int OTHER_BUYERS = 15;
    private static final int ROUNDS = 10;
    private static final int REPEATED_CANCELS = 10;

    @Autowired
    private HoldService holdService;

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
    @DisplayName("취소하는 동안 다른 구매자들이 같은 좌석을 동시에 선점해도 그 좌석은 많아야 한 주문에만 잡힌다")
    void concurrentHoldsDuringCancelHoldSeatAtMostOnce() throws Exception {
        Showtime showtime = createShowtime();
        Long seatId = saveSeat(showtime, "A1");
        String cancelled = hold(1L, showtime, seatId);

        List<Callable<String>> tasks = new ArrayList<>();
        tasks.add(() -> orderService.cancel(1L, cancelled).status().name());
        for (long buyerId = 2; buyerId < 2 + OTHER_BUYERS; buyerId++) {
            long buyer = buyerId;
            tasks.add(() -> tryHold(buyer, showtime, seatId));
        }
        List<String> results = runConcurrently(tasks);

        assertThat(results.getFirst()).isEqualTo("CANCELLED");
        List<String> heldTokens = results.subList(1, results.size()).stream().filter(token -> token != null).toList();
        assertThat(heldTokens).hasSizeLessThanOrEqualTo(1);
        assertThat(orderStatus(cancelled)).isEqualTo("CANCELLED");
        assertThat(count("SELECT COUNT(*) FROM orders WHERE status = 'HELD'")).isEqualTo(heldTokens.size());
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(1 + heldTokens.size());
        if (heldTokens.isEmpty()) {
            assertThat(seatStatus(seatId)).isEqualTo("AVAILABLE");
        } else {
            assertThat(heldSeatsOwnedBy(heldTokens.getFirst())).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("같은 주문에 취소와 확정이 동시에 와도 하나만 성공하고 주문과 좌석 상태가 성공한 쪽과 맞는다")
    void concurrentCancelAndConfirmLetOnlyOneWin() throws Exception {
        Showtime showtime = createShowtime();
        for (int round = 0; round < ROUNDS; round++) {
            Long buyerId = round + 1L;
            Long seatId = saveSeat(showtime, "A" + round);
            String token = hold(buyerId, showtime, seatId);

            List<String> results = runConcurrently(List.of(
                    () -> attempt(() -> orderService.cancel(buyerId, token)),
                    () -> attempt(() -> orderService.confirm(buyerId, token))));

            String cancel = results.get(0);
            String confirm = results.get(1);
            if (cancel.equals("CANCELLED")) {
                assertThat(confirm).isEqualTo("ORDER_CANCELLED");
                assertThat(orderStatus(token)).isEqualTo("CANCELLED");
                assertThat(jdbcTemplate.queryForMap(
                        "SELECT status, current_order_id, held_until FROM seat WHERE id = ?", seatId))
                        .containsEntry("status", "AVAILABLE")
                        .containsEntry("current_order_id", null)
                        .containsEntry("held_until", null);
                assertThat(jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM order_seat os JOIN orders o ON o.id = os.order_id
                        WHERE o.token = ? AND os.seat_id = ?
                        """, Integer.class, token, seatId)).isEqualTo(1);
            } else {
                assertThat(cancel).isEqualTo("ORDER_CONFIRMED");
                assertThat(confirm).isEqualTo("CONFIRMED");
                assertThat(orderStatus(token)).isEqualTo("CONFIRMED");
                assertThat(seatStatus(seatId)).isEqualTo("SOLD");
                assertThat(soldSeatsOwnedBy(token)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("같은 주문에 취소를 동시에 여러 번 보내도 모두 취소된 주문을 받고 좌석은 한 번만 풀린다")
    void concurrentCancelsOfSameOrderReturnSameResult() throws Exception {
        Showtime showtime = createShowtime();
        Long first = saveSeat(showtime, "A1");
        Long second = saveSeat(showtime, "A2");
        String token = holdService.hold(1L, UUID.randomUUID(),
                new CreateOrderRequest(showtime.getId(), List.of(first, second), null, "밤산책")).token();

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < REPEATED_CANCELS; i++) {
            tasks.add(() -> attempt(() -> orderService.cancel(1L, token)));
        }
        List<String> results = runConcurrently(tasks);

        assertThat(results).containsOnly("CANCELLED");
        assertThat(orderStatus(token)).isEqualTo("CANCELLED");
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'AVAILABLE'"
                + " AND current_order_id IS NULL AND held_until IS NULL")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(2);
    }

    private String hold(Long buyerId, Showtime showtime, Long seatId) {
        return holdService.hold(buyerId, UUID.randomUUID(),
                new CreateOrderRequest(showtime.getId(), List.of(seatId), null, "구매자" + buyerId)).token();
    }

    private String tryHold(Long buyerId, Showtime showtime, Long seatId) {
        try {
            return hold(buyerId, showtime, seatId);
        } catch (SeatHoldRejectedException e) {
            assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SEAT_NOT_AVAILABLE);
            return null;
        }
    }

    private String attempt(Callable<OrderResponse> action) throws Exception {
        try {
            return action.call().status().name();
        } catch (OrderRejectedException e) {
            return e.getRejection().name();
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

    private Showtime createShowtime() {
        Performance performance = performanceRepository.save(Performance.create("취소 동시성 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, SeatType.RESERVED,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, 4));
    }

    private Long saveSeat(Showtime showtime, String label) {
        return seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, label)).getId();
    }

    private int heldSeatsOwnedBy(String token) {
        return seatsOwnedBy(token, "HELD");
    }

    private int soldSeatsOwnedBy(String token) {
        return seatsOwnedBy(token, "SOLD");
    }

    private int seatsOwnedBy(String token, String status) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM seat s
                JOIN orders o ON o.id = s.current_order_id
                JOIN order_seat os ON os.order_id = o.id AND os.seat_id = s.id
                WHERE o.token = ? AND s.status = ?
                """, Integer.class, token, status);
    }

    private String orderStatus(String token) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE token = ?", String.class, token);
    }

    private String seatStatus(Long seatId) {
        return jdbcTemplate.queryForObject("SELECT status FROM seat WHERE id = ?", String.class, seatId);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
