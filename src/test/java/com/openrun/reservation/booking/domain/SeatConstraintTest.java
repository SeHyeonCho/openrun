package com.openrun.reservation.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openrun.reservation.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SeatConstraintTest {

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Performance performance;

    @BeforeEach
    void setUp() {
        performance = performanceRepository.save(Performance.create("제약 테스트 공연"));
    }

    @Test
    @DisplayName("한 회차 안에서는 같은 좌석 번호를 두 번 등록할 수 없다")
    void rejectsDuplicateLabelInSameShowtime() {
        Showtime showtime = createShowtime();
        seatRepository.saveAndFlush(Seat.forSale(showtime, SeatType.RESERVED, "A1"));

        assertThatThrownBy(() -> seatRepository.saveAndFlush(Seat.forSale(showtime, SeatType.RESERVED, "A1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 회차에서는 같은 좌석 번호를 각각 등록할 수 있다")
    void allowsSameLabelInDifferentShowtimes() {
        Showtime first = createShowtime();
        Showtime second = createShowtime();

        seatRepository.saveAndFlush(Seat.forSale(first, SeatType.RESERVED, "A1"));
        seatRepository.saveAndFlush(Seat.forSale(second, SeatType.RESERVED, "A1"));

        assertThat(seatRepository.findByShowtimeIdOrderByIdAsc(first.getId())).hasSize(1);
        assertThat(seatRepository.findByShowtimeIdOrderByIdAsc(second.getId())).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "status = 'HELD', current_order_id = NULL, held_until = NULL",
            "status = 'HELD', held_until = NULL",
            "status = 'HELD', current_order_id = NULL",
            "status = 'SOLD', held_until = '2026-11-04 10:00:00'",
            "status = 'SOLD', current_order_id = NULL, held_until = NULL",
            "status = 'AVAILABLE'",
            "status = 'NOT_FOR_SALE', held_until = NULL"
    })
    @DisplayName("좌석 상태와 맞지 않는 주문이나 선점 기한은 DB가 저장을 거부한다")
    void rejectsSeatStateMismatch(String assignments) {
        Long seatId = createHeldSeat().getId();

        assertThatThrownBy(() -> jdbcTemplate.update("update seat set " + assignments + " where id = ?", seatId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_seat_state");
    }

    @Test
    @DisplayName("상태에 맞는 주문과 선점 기한을 가진 좌석은 저장할 수 있다")
    void allowsConsistentSeatStates() {
        Seat seat = createHeldSeat();
        Long seatId = seat.getId();

        assertThatCode(() -> {
            jdbcTemplate.update("update seat set status = 'SOLD', held_until = NULL where id = ?", seatId);
            jdbcTemplate.update("update seat set status = 'AVAILABLE', current_order_id = NULL where id = ?", seatId);
            jdbcTemplate.update("update seat set status = 'NOT_FOR_SALE' where id = ?", seatId);
        }).doesNotThrowAnyException();
    }

    private Seat createHeldSeat() {
        Showtime showtime = createShowtime();
        Seat seat = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1"));
        Instant now = Instant.now();
        seat.hold(orderRepository.save(Order.hold(showtime, 1L, now)), now);
        seatRepository.flush();
        return seat;
    }

    private Showtime createShowtime() {
        Instant startsAt = Instant.now().plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, startsAt, startsAt.minus(Duration.ofDays(3)), 10, 4));
    }
}
