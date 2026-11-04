package com.openrun.reservation.booking.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.openrun.reservation.booking.domain.ShowtimeBuyerLock;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, MutableClockConfiguration.class})
class OrderConfirmConcurrencyTest {

    private static final Instant OPENED_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant HELD_UNTIL = OPENED_AT.plus(Duration.ofMinutes(10));
    private static final Instant JUST_BEFORE_HELD_UNTIL = HELD_UNTIL.minus(1, ChronoUnit.MICROS);
    private static final int OTHER_BUYERS = 49;
    private static final int DUPLICATE_CONFIRMS = 20;
    private static final int BOUNDARY_ROUNDS = 10;

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
    private ShowtimeBuyerLock showtimeBuyerLock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("기한 경계에서 원래 구매자의 확정과 다른 구매자들의 재선점이 몰려도 한쪽만 성공하고 좌석은 한 주문에만 속한다")
    void confirmAndHoldAtHeldUntilNeverBothSucceed() throws Exception {
        for (int round = 0; round < BOUNDARY_ROUNDS; round++) {
            Showtime showtime = createShowtime(4);
            Long seatId = saveSeat(showtime, "A1");
            Long originalOrderId = holdAtOpening(showtime, 1L, seatId);

            List<Callable<Outcome>> tasks = new ArrayList<>();
            tasks.add(() -> arrivingAt(JUST_BEFORE_HELD_UNTIL, () -> confirm(originalOrderId, 1L)));
            for (long buyer = 2; buyer < 2 + OTHER_BUYERS; buyer++) {
                long userId = buyer;
                tasks.add(() -> arrivingAt(HELD_UNTIL, () -> hold(showtime, userId, seatId)));
            }
            List<Outcome> outcomes = runConcurrently(tasks);

            assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
            assertThat(outcomes.getFirst().succeeded() || outcomes.getFirst().rejection().equals("ORDER_EXPIRED"))
                    .isTrue();
            assertThat(outcomes.subList(1, outcomes.size())).filteredOn(outcome -> !outcome.succeeded())
                    .allMatch(outcome -> outcome.rejection().equals("SEAT_NOT_AVAILABLE"));
            assertThat(count("""
                    SELECT COUNT(*) FROM order_seat os JOIN orders o ON o.id = os.order_id
                    WHERE os.seat_id = %d AND o.status IN ('HELD', 'CONFIRMED')
                    """.formatted(seatId))).isEqualTo(1);
            if (outcomes.getFirst().succeeded()) {
                assertThat(orderStatus(originalOrderId)).isEqualTo("CONFIRMED");
                assertThat(count("""
                        SELECT COUNT(*) FROM seat
                        WHERE id = %d AND status = 'SOLD' AND current_order_id = %d AND held_until IS NULL
                        """.formatted(seatId, originalOrderId))).isEqualTo(1);
            } else {
                assertThat(orderStatus(originalOrderId)).isEqualTo("EXPIRED");
                assertThat(count("""
                        SELECT COUNT(*) FROM seat s JOIN orders o ON o.id = s.current_order_id
                        WHERE s.id = %d AND s.status = 'HELD' AND o.status = 'HELD' AND s.held_until = o.held_until
                        """.formatted(seatId))).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("같은 주문을 동시에 여러 번 확정해도 모두 같은 응답을 받고 주문과 좌석은 한 번 확정한 상태다")
    void concurrentConfirmsOfSameOrderReturnSameResult() throws Exception {
        Showtime showtime = createShowtime(4);
        Long first = saveSeat(showtime, "A1");
        Long second = saveSeat(showtime, "A2");
        clock.setInstant(OPENED_AT);
        Long orderId = orderService.hold(new CreateOrderRequest(showtime.getId(), 1L, List.of(first, second)))
                .orderId();
        clock.setInstant(OPENED_AT.plus(Duration.ofMinutes(1)));

        List<Callable<OrderResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < DUPLICATE_CONFIRMS; i++) {
            tasks.add(() -> orderService.confirm(orderId, new ConfirmOrderRequest(1L)));
        }
        List<OrderResponse> responses = runConcurrently(tasks);

        assertThat(responses).hasSize(DUPLICATE_CONFIRMS).containsOnly(responses.getFirst());
        assertThat(responses.getFirst().status().name()).isEqualTo("CONFIRMED");
        assertThat(responses.getFirst().seatIds()).containsExactly(first, second);
        assertThat(orderStatus(orderId)).isEqualTo("CONFIRMED");
        assertThat(count("""
                SELECT COUNT(*) FROM seat
                WHERE status = 'SOLD' AND current_order_id = %d AND held_until IS NULL
                """.formatted(orderId))).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 구매자의 다른 요청이 구매자 잠금을 쥐고 있으면 확정은 그 요청이 끝날 때까지 기다린다")
    void confirmWaitsForSameBuyerRequest() throws Exception {
        Showtime showtime = createShowtime(4);
        Long seatId = saveSeat(showtime, "A1");
        Long orderId = holdAtOpening(showtime, 1L, seatId);
        clock.setInstant(OPENED_AT.plus(Duration.ofMinutes(1)));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            LockHolder holder = holdBuyerLock(executor, showtime.getId(), 1L);
            Future<Outcome> confirm = executor.submit(() -> attempt(() -> confirm(orderId, 1L)));

            awaitBuyerLockWait();
            assertThat(confirm.isDone()).isFalse();

            holder.release();
            assertThat(confirm.get(30, TimeUnit.SECONDS).succeeded()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("확정은 잠금을 기다린 뒤의 시각으로 기한을 판단한다")
    void confirmJudgesHeldUntilAfterWaitingForLocks() throws Exception {
        Showtime showtime = createShowtime(4);
        Long seatId = saveSeat(showtime, "A1");
        Long orderId = holdAtOpening(showtime, 1L, seatId);
        clock.setInstant(JUST_BEFORE_HELD_UNTIL);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            LockHolder holder = holdBuyerLock(executor, showtime.getId(), 1L);
            Future<Outcome> confirm = executor.submit(() -> attempt(() -> confirm(orderId, 1L)));
            awaitBuyerLockWait();

            clock.setInstant(HELD_UNTIL);
            holder.release();

            assertThat(confirm.get(30, TimeUnit.SECONDS).rejection()).isEqualTo("ORDER_EXPIRED");
            assertThat(orderStatus(orderId)).isEqualTo("HELD");
            assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'HELD' AND id = " + seatId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private LockHolder holdBuyerLock(ExecutorService executor, Long showtimeId, Long userId) throws Exception {
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> transaction = executor.submit(() -> new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> {
                    showtimeBuyerLock.acquire(showtimeId, userId);
                    acquired.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
        assertThat(acquired.await(10, TimeUnit.SECONDS)).isTrue();
        return new LockHolder(release, transaction);
    }

    private void awaitBuyerLockWait() throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            if (count("""
                    SELECT COUNT(*) FROM information_schema.PROCESSLIST
                    WHERE ID <> CONNECTION_ID() AND COMMAND = 'Query' AND INFO LIKE '%INSERT INTO showtime_buyer%'
                    """) > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("확정 요청이 구매자 잠금을 기다리지 않았다");
    }

    private Outcome arrivingAt(Instant arrival, Runnable action) {
        clock.setArrivalForCurrentThread(arrival);
        try {
            return attempt(action);
        } finally {
            clock.clearArrivalForCurrentThread();
        }
    }

    private Outcome attempt(Runnable action) {
        try {
            action.run();
            return Outcome.success();
        } catch (SeatHoldRejectedException e) {
            return Outcome.rejected(e.getRejection().name());
        } catch (OrderConfirmRejectedException e) {
            return Outcome.rejected(e.getRejection().name());
        }
    }

    private void confirm(Long orderId, Long userId) {
        orderService.confirm(orderId, new ConfirmOrderRequest(userId));
    }

    private void hold(Showtime showtime, Long userId, Long seatId) {
        orderService.hold(new CreateOrderRequest(showtime.getId(), userId, List.of(seatId)));
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
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
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();
        return results;
    }

    private Showtime createShowtime(int maxSeatsPerUser) {
        Performance performance = performanceRepository.save(Performance.create("확정 동시성 테스트 공연"));
        return showtimeRepository.save(Showtime.create(performance, OPENED_AT.plus(Duration.ofDays(7)),
                OPENED_AT.minus(Duration.ofHours(1)), 10, maxSeatsPerUser));
    }

    private Long saveSeat(Showtime showtime, String label) {
        return seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, label)).getId();
    }

    private Long holdAtOpening(Showtime showtime, Long userId, Long seatId) {
        clock.setInstant(OPENED_AT);
        return orderService.hold(new CreateOrderRequest(showtime.getId(), userId, List.of(seatId))).orderId();
    }

    private String orderStatus(Long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private record LockHolder(CountDownLatch releaseSignal, Future<?> transaction) {

        void release() throws Exception {
            releaseSignal.countDown();
            transaction.get(30, TimeUnit.SECONDS);
        }
    }

    private record Outcome(boolean succeeded, String rejection) {

        static Outcome success() {
            return new Outcome(true, null);
        }

        static Outcome rejected(String rejection) {
            return new Outcome(false, rejection);
        }
    }
}
