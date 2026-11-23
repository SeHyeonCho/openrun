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
import com.openrun.reservation.booking.domain.ShowtimeBuyerLock;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, MutableClockConfiguration.class})
class SalesCloseTest {

    private static final Instant SALES_OPEN_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant STARTS_AT = SALES_OPEN_AT.plus(Duration.ofDays(7));
    private static final Instant SALES_CLOSE_AT = STARTS_AT.minus(Duration.ofHours(1));
    private static final Instant JUST_BEFORE_CLOSE = SALES_CLOSE_AT.minus(1, ChronoUnit.MICROS);
    private static final int HOLD_MINUTES = 10;
    private static final int GENERAL_SEATS = 10;
    private static final int BUYERS = 20;

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
    private ShowtimeBuyerLock showtimeBuyerLock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("판매 마감 직전에는 지정석을 선점하고, 마감 시각부터는 SALES_CLOSED로 거부하며 좌석과 주문은 그대로다")
    void reservedHoldClosesAtSalesCloseAt() {
        Showtime showtime = createShowtime(SeatType.RESERVED);
        Long first = saveSeat(showtime, SeatType.RESERVED, "A1");
        Long second = saveSeat(showtime, SeatType.RESERVED, "A2");

        clock.setInstant(JUST_BEFORE_CLOSE);
        holdService.hold(1L, UUID.randomUUID(), reserved(showtime, first));

        clock.setInstant(SALES_CLOSE_AT);
        assertRejected(() -> holdService.hold(2L, UUID.randomUUID(), reserved(showtime, second)));
        assertThat(seatStatus(second)).isEqualTo("AVAILABLE");
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
    }

    @Test
    @DisplayName("판매 마감 직전에는 자유석을 선점하고, 마감 시각부터는 SALES_CLOSED로 거부한다")
    void generalHoldClosesAtSalesCloseAt() {
        Showtime showtime = createShowtime(SeatType.GENERAL);
        saveSeat(showtime, SeatType.GENERAL, "1");
        saveSeat(showtime, SeatType.GENERAL, "2");

        clock.setInstant(JUST_BEFORE_CLOSE);
        holdService.hold(1L, UUID.randomUUID(), general(showtime, 1));

        clock.setInstant(SALES_CLOSE_AT);
        assertRejected(() -> holdService.hold(2L, UUID.randomUUID(), general(showtime, 1)));
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'HELD'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
    }

    @Test
    @DisplayName("판매 마감 전에 잡은 선점은 마감 뒤에도 선점 기한 안에 확정하고 취소할 수 있다")
    void holdsBeforeCloseCanBeConfirmedOrCancelledAfterClose() {
        Showtime showtime = createShowtime(SeatType.RESERVED);
        Long confirmedSeat = saveSeat(showtime, SeatType.RESERVED, "A1");
        Long cancelledSeat = saveSeat(showtime, SeatType.RESERVED, "A2");
        clock.setInstant(JUST_BEFORE_CLOSE);
        String toConfirm = holdService.hold(1L, UUID.randomUUID(), reserved(showtime, confirmedSeat)).token();
        String toCancel = holdService.hold(2L, UUID.randomUUID(), reserved(showtime, cancelledSeat)).token();

        clock.setInstant(SALES_CLOSE_AT.plus(Duration.ofMinutes(HOLD_MINUTES - 1)));

        assertThat(orderService.confirm(1L, toConfirm).status().name()).isEqualTo("CONFIRMED");
        assertThat(orderService.cancel(2L, toCancel).status().name()).isEqualTo("CANCELLED");
        assertThat(seatStatus(confirmedSeat)).isEqualTo("SOLD");
        assertThat(seatStatus(cancelledSeat)).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("SALES_CLOSED로 끝난 선점 요청은 기록되어, 같은 식별자로 다시 보내면 같은 409를 받는다")
    void salesClosedRejectionIsReplayed() {
        Showtime showtime = createShowtime(SeatType.RESERVED);
        Long seatId = saveSeat(showtime, SeatType.RESERVED, "A1");
        UUID key = UUID.randomUUID();
        clock.setInstant(SALES_CLOSE_AT);

        assertRejected(() -> holdService.hold(1L, key, reserved(showtime, seatId)));
        assertRejected(() -> holdService.hold(1L, key, reserved(showtime, seatId)));

        assertThat(jdbcTemplate.queryForObject("SELECT rejection_code FROM hold_request", String.class))
                .isEqualTo("SALES_CLOSED");
        assertThat(count("SELECT COUNT(*) FROM hold_request")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
    }

    @Test
    @DisplayName("마감 전에 받은 선점이 구매자 잠금을 기다리는 동안 마감 시각이 지나면 SALES_CLOSED로 거부하고 주문을 만들지 않는다")
    void holdThatWaitedForBuyerLockPastSalesCloseIsRejected() throws Exception {
        Showtime showtime = createShowtime(SeatType.RESERVED);
        Long seatId = saveSeat(showtime, SeatType.RESERVED, "A1");
        clock.setInstant(JUST_BEFORE_CLOSE);

        Throwable result = holdWhileBlocked(() -> showtimeBuyerLock.acquire(showtime.getId(), 1L),
                "INSERT INTO showtime_buyer%", reserved(showtime, seatId));

        assertSalesClosed(result);
        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isZero();
        assertThat(seatStatus(seatId)).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("구매자 잠금을 기다리는 사이 마감이 지나면 좌석이 다른 주문에 선점돼 있어도 SEAT_NOT_AVAILABLE이 아니라 SALES_CLOSED로 거부한다")
    void salesClosedComesBeforeSeatCheckAfterBuyerLockWait() throws Exception {
        Showtime showtime = createShowtime(SeatType.RESERVED);
        Long seatId = saveSeat(showtime, SeatType.RESERVED, "A1");
        clock.setInstant(JUST_BEFORE_CLOSE);
        holdService.hold(2L, UUID.randomUUID(), reserved(showtime, seatId));
        Map<String, Object> before = dbState(seatId);

        Throwable result = holdWhileBlocked(() -> showtimeBuyerLock.acquire(showtime.getId(), 1L),
                "INSERT INTO showtime_buyer%", reserved(showtime, seatId));

        assertSalesClosed(result);
        assertThat(dbState(seatId)).isEqualTo(before);
    }

    @Test
    @DisplayName("좌석 잠금을 기다리는 사이 마감이 지나면 좌석이 다른 주문에 선점돼 있어도 SALES_CLOSED로 거부한다")
    void salesClosedComesBeforeSeatCheckAfterSeatLockWait() throws Exception {
        Showtime showtime = createShowtime(SeatType.RESERVED);
        Long seatId = saveSeat(showtime, SeatType.RESERVED, "A1");
        clock.setInstant(JUST_BEFORE_CLOSE);
        holdService.hold(2L, UUID.randomUUID(), reserved(showtime, seatId));
        Map<String, Object> before = dbState(seatId);

        Throwable result = holdWhileBlocked(
                () -> jdbcTemplate.queryForList("SELECT id FROM seat WHERE id = ? FOR UPDATE", seatId),
                "select%from seat%for update%", reserved(showtime, seatId));

        assertSalesClosed(result);
        assertThat(dbState(seatId)).isEqualTo(before);
    }

    @Test
    @DisplayName("마감 직전 자유석 재고보다 많은 구매자가 동시에 선점해도 재고를 넘지 않고, 마감 시각의 동시 선점은 모두 SALES_CLOSED이며 아무것도 바꾸지 않는다")
    void concurrentGeneralHoldsAroundSalesCloseNeverExceedStock() throws Exception {
        Showtime showtime = createShowtime(SeatType.GENERAL);
        for (int i = 1; i <= GENERAL_SEATS; i++) {
            saveSeat(showtime, SeatType.GENERAL, String.valueOf(i));
        }

        clock.setInstant(JUST_BEFORE_CLOSE);
        List<Throwable> beforeClose = holdConcurrently(showtime, 1L);
        long succeeded = beforeClose.stream().filter(result -> result == null).count();
        assertThat(succeeded).isPositive().isLessThanOrEqualTo(GENERAL_SEATS);
        assertThat(beforeClose).filteredOn(result -> result != null).allSatisfy(result ->
                assertThat(result).isInstanceOfSatisfying(SeatHoldRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SEAT_NOT_AVAILABLE)));
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo((int) succeeded);
        assertThat(count("""
                SELECT COUNT(*) FROM seat s
                JOIN orders o ON o.id = s.current_order_id
                JOIN order_seat os ON os.order_id = o.id AND os.seat_id = s.id
                WHERE s.status = 'HELD'
                """)).isEqualTo((int) succeeded);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo((int) succeeded);

        clock.setInstant(SALES_CLOSE_AT);
        List<Throwable> atClose = holdConcurrently(showtime, 1L + BUYERS);
        assertThat(atClose).allSatisfy(this::assertSalesClosed);
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo((int) succeeded);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'HELD'")).isEqualTo((int) succeeded);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo((int) succeeded);
    }

    private List<Throwable> holdConcurrently(Showtime showtime, long firstBuyerId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(BUYERS);
        List<Throwable> results = new ArrayList<>();
        try {
            CountDownLatch ready = new CountDownLatch(BUYERS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Throwable>> futures = new ArrayList<>();
            for (long buyerId = firstBuyerId; buyerId < firstBuyerId + BUYERS; buyerId++) {
                long buyer = buyerId;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        holdService.hold(buyer, UUID.randomUUID(), general(showtime, 1));
                        return null;
                    } catch (SeatHoldRejectedException e) {
                        return e;
                    }
                }));
            }
            ready.await();
            start.countDown();
            for (Future<Throwable> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
        return results;
    }

    private Map<String, Object> dbState(Long seatId) {
        Map<String, Object> state = new HashMap<>(jdbcTemplate.queryForMap(
                "SELECT status, current_order_id, held_until FROM seat WHERE id = ?", seatId));
        state.put("orders", count("SELECT COUNT(*) FROM orders"));
        state.put("order_seat", count("SELECT COUNT(*) FROM order_seat"));
        return state;
    }

    private Throwable holdWhileBlocked(Runnable lock, String waitingQuery, CreateOrderRequest request)
            throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> blocker = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                lock.run();
                locked.countDown();
                awaitQuietly(release);
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Throwable> hold = executor.submit(() -> {
                try {
                    holdService.hold(1L, UUID.randomUUID(), request);
                    return null;
                } catch (Throwable e) {
                    return e;
                }
            });
            waitUntilWaiting(waitingQuery);

            clock.setInstant(SALES_CLOSE_AT);
            release.countDown();
            blocker.get(10, TimeUnit.SECONDS);
            return hold.get(10, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void waitUntilWaiting(String waitingQuery) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.processlist"
                + " WHERE id <> CONNECTION_ID() AND info LIKE ?", Integer.class, waitingQuery) == 0) {
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(20);
        }
    }

    private void assertSalesClosed(Throwable result) {
        assertThat(result).isInstanceOfSatisfying(SeatHoldRejectedException.class,
                e -> assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SALES_CLOSED));
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void assertRejected(Runnable hold) {
        assertThatThrownBy(hold::run)
                .isInstanceOfSatisfying(SeatHoldRejectedException.class,
                        e -> assertThat(e.getRejection()).isEqualTo(SeatHoldRejection.SALES_CLOSED));
    }

    private CreateOrderRequest reserved(Showtime showtime, Long seatId) {
        return new CreateOrderRequest(showtime.getId(), List.of(seatId), null, "밤산책");
    }

    private CreateOrderRequest general(Showtime showtime, int quantity) {
        return new CreateOrderRequest(showtime.getId(), null, quantity, "밤산책");
    }

    private Showtime createShowtime(SeatType seatType) {
        Performance performance = performanceRepository.save(Performance.create("판매 마감 테스트 공연", "테스트 공연장"));
        return showtimeRepository.save(Showtime.create(performance, seatType, STARTS_AT,
                STARTS_AT.plus(Duration.ofHours(2)), SALES_OPEN_AT, SALES_CLOSE_AT, HOLD_MINUTES, 4));
    }

    private Long saveSeat(Showtime showtime, SeatType seatType, String label) {
        return seatRepository.save(Seat.forSale(showtime, seatType, label)).getId();
    }

    private String seatStatus(Long seatId) {
        return jdbcTemplate.queryForObject("SELECT status FROM seat WHERE id = ?", String.class, seatId);
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
