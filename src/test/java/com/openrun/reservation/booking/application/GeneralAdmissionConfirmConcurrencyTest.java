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
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
class GeneralAdmissionConfirmConcurrencyTest {

    private static final int ORDERS = 8;
    private static final int QUANTITY = 2;
    private static final int DUPLICATE_CONFIRMS = 20;

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
    @DisplayName("같은 회차의 여러 주문을 동시에 확정해도 입장번호가 겹치거나 비지 않고, 주문마다 이어진 번호를 받는다")
    void concurrentConfirmsOfDifferentOrdersGetDistinctContiguousNumbers() throws Exception {
        Showtime showtime = createGeneralShowtime(ORDERS * QUANTITY);
        List<Long> buyers = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        for (long buyer = 1; buyer <= ORDERS; buyer++) {
            buyers.add(buyer);
            tokens.add(holdGeneral(showtime, buyer, QUANTITY).token());
        }

        List<Callable<OrderResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) {
            Long buyer = buyers.get(i);
            String token = tokens.get(i);
            tasks.add(() -> orderService.confirm(buyer, token));
        }
        List<OrderResponse> responses = runConcurrently(tasks);

        assertThat(responses).allSatisfy(response -> {
            assertThat(response.status().name()).isEqualTo("CONFIRMED");
            assertThat(response.entryNumbers()).hasSize(QUANTITY);
            assertThat(response.entryNumbers().get(1)).isEqualTo(response.entryNumbers().get(0) + 1);
        });
        assertThat(responses.stream().flatMap(response -> response.entryNumbers().stream()).sorted().toList())
                .isEqualTo(IntStream.rangeClosed(1, ORDERS * QUANTITY).boxed().toList());
        assertThat(count("SELECT COUNT(*) FROM order_entry_number")).isEqualTo(ORDERS * QUANTITY);
        assertThat(count("SELECT last_number FROM showtime_entry_sequence WHERE showtime_id = " + showtime.getId()))
                .isEqualTo(ORDERS * QUANTITY);
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(ORDERS);
        assertThat(count("SELECT COUNT(*) FROM orders WHERE status = 'CONFIRMED'")).isEqualTo(ORDERS);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(ORDERS * QUANTITY);
        assertThat(soldSeatsOwnedByTheirOrder()).isEqualTo(ORDERS * QUANTITY);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status <> 'SOLD'")).isZero();
    }

    @Test
    @DisplayName("같은 주문에 확정을 동시에 여러 번 보내도 입장번호는 한 번만 배정되고, 성공한 요청은 모두 같은 번호를 받는다")
    void concurrentConfirmsOfSameOrderAssignNumbersOnce() throws Exception {
        Showtime showtime = createGeneralShowtime(5);
        holdGeneral(showtime, 9L, 1);
        String token = holdGeneral(showtime, 1L, 3).token();

        List<Callable<OrderResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < DUPLICATE_CONFIRMS; i++) {
            tasks.add(() -> orderService.confirm(1L, token));
        }
        List<OrderResponse> responses = runConcurrently(tasks);

        assertThat(responses).extracting(OrderResponse::entryNumbers).containsOnly(List.of(1, 2, 3));
        assertThat(count("SELECT COUNT(*) FROM order_entry_number")).isEqualTo(3);
        assertThat(count("SELECT last_number FROM showtime_entry_sequence WHERE showtime_id = " + showtime.getId()))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(4);
        assertThat(count("SELECT COUNT(*) FROM orders WHERE buyer_id = 1 AND status = 'CONFIRMED'")).isEqualTo(1);
        assertThat(soldSeatsOwnedByTheirOrder()).isEqualTo(3);
        assertThat(count("""
                SELECT COUNT(*) FROM seat s
                JOIN order_seat os ON os.seat_id = s.id
                JOIN orders o ON o.id = os.order_id
                WHERE o.buyer_id = 9 AND o.status = 'HELD'
                  AND s.status = 'HELD' AND s.current_order_id = o.id AND s.held_until = o.held_until
                """)).isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM order_entry_number e JOIN orders o ON o.id = e.order_id WHERE o.buyer_id = 9
                """)).isZero();
    }

    private int soldSeatsOwnedByTheirOrder() {
        return count("""
                SELECT COUNT(*) FROM seat s
                JOIN order_seat os ON os.seat_id = s.id
                JOIN orders o ON o.id = os.order_id
                WHERE s.status = 'SOLD' AND s.current_order_id = o.id AND s.held_until IS NULL
                  AND o.status = 'CONFIRMED'
                """);
    }

    private OrderResponse holdGeneral(Showtime showtime, Long buyerId, int quantity) {
        return holdService.hold(buyerId, UUID.randomUUID(),
                new CreateOrderRequest(showtime.getId(), null, quantity, "구매자" + buyerId));
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
        Performance performance = performanceRepository.save(Performance.create("입장번호 동시성 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, 4));
        for (int i = 1; i <= seats; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        return showtime;
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
