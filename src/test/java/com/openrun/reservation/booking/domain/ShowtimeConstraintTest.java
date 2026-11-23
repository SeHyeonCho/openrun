package com.openrun.reservation.booking.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openrun.reservation.TestcontainersConfiguration;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ShowtimeConstraintTest {

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("판매 마감 시각이 판매 시작 시각보다 늦지 않거나 공연 종료 시각보다 늦으면 DB가 저장을 거부한다")
    void rejectsSalesCloseOutOfRange() {
        Long showtimeId = createShowtime();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE showtime SET sales_close_at = sales_open_at WHERE id = ?", showtimeId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_showtime_sales_close_after_open");
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE showtime SET sales_close_at = ends_at + INTERVAL 1 MICROSECOND WHERE id = ?", showtimeId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ck_showtime_sales_close_until_end");
    }

    @Test
    @DisplayName("판매 마감 시각은 공연 시작 전이나 공연 종료 시각으로 저장할 수 있다")
    void allowsSalesCloseWithinRange() {
        Long showtimeId = createShowtime();

        assertThatCode(() -> jdbcTemplate.update(
                "UPDATE showtime SET sales_close_at = starts_at - INTERVAL 1 HOUR WHERE id = ?", showtimeId))
                .doesNotThrowAnyException();
        assertThatCode(() -> jdbcTemplate.update(
                "UPDATE showtime SET sales_close_at = ends_at WHERE id = ?", showtimeId))
                .doesNotThrowAnyException();
    }

    private Long createShowtime() {
        Performance performance = performanceRepository.save(Performance.create("회차 제약 테스트 공연", "테스트 공연장"));
        Instant startsAt = Instant.now().plus(Duration.ofDays(7));
        return showtimeRepository.saveAndFlush(Showtime.create(performance, SeatType.RESERVED,
                startsAt, startsAt.plus(Duration.ofHours(2)), startsAt.minus(Duration.ofDays(3)), 10, 4)).getId();
    }
}
