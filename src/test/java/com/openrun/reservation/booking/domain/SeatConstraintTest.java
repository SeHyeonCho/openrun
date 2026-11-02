package com.openrun.reservation.booking.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SeatConstraintTest {

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

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

    private Showtime createShowtime() {
        Instant startsAt = Instant.now().plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, startsAt, startsAt.minus(Duration.ofDays(3)), 10, 4));
    }
}
