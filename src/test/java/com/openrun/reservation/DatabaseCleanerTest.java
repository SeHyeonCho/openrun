package com.openrun.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.SeatType;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
class DatabaseCleanerTest {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("커밋된 데이터를 외래 키 순서와 상관없이 모두 지우고 마이그레이션 기록은 남긴다")
    void removesCommittedDataAndKeepsMigrationHistory() {
        Performance performance = performanceRepository.save(Performance.create("정리 테스트 공연", "테스트 공연장"));
        Instant startsAt = Instant.now().plus(Duration.ofDays(7));
        Showtime showtime = showtimeRepository.save(
                Showtime.create(performance, startsAt, startsAt.plus(Duration.ofHours(2)),
                        startsAt.minus(Duration.ofDays(3)), 10, 4));
        seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1"));

        databaseCleaner.clean();

        assertThat(performanceRepository.count()).isZero();
        assertThat(showtimeRepository.count()).isZero();
        assertThat(seatRepository.count()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class))
                .isPositive();
    }
}
