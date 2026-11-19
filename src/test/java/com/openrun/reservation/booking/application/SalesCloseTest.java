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
import java.time.temporal.ChronoUnit;
import java.util.List;
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
class SalesCloseTest {

    private static final Instant SALES_OPEN_AT = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant STARTS_AT = SALES_OPEN_AT.plus(Duration.ofDays(7));
    private static final Instant SALES_CLOSE_AT = STARTS_AT.minus(Duration.ofHours(1));
    private static final Instant JUST_BEFORE_CLOSE = SALES_CLOSE_AT.minus(1, ChronoUnit.MICROS);
    private static final int HOLD_MINUTES = 10;

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
