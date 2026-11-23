package com.openrun.reservation.booking.api;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class PerformanceApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("로그인하지 않아도 공연과 회차의 판매 조건, 판매 상태, 남은 수, 서버 시각을 반환한다")
    void returnsPerformanceWithShowtimes() throws Exception {
        Performance performance = performanceRepository.save(
                Performance.create("공연 조회 테스트 공연", "테스트 공연장", "/mock/night-signal-poster.png"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL, startsAt,
                startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), startsAt, 10, 4));
        seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, "1"));
        seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, "2"));
        seatRepository.save(Seat.notForSale(showtime, SeatType.GENERAL, "3"));

        mockMvc.perform(get("/api/performances/{performanceId}", performance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(performance.getId()))
                .andExpect(jsonPath("$.title").value("공연 조회 테스트 공연"))
                .andExpect(jsonPath("$.venue").value("테스트 공연장"))
                .andExpect(jsonPath("$.posterUrl").value("/mock/night-signal-poster.png"))
                .andExpect(jsonPath("$.showtimes[0].id").value(showtime.getId()))
                .andExpect(jsonPath("$.showtimes[0].startsAt").isString())
                .andExpect(jsonPath("$.showtimes[0].endsAt").isString())
                .andExpect(jsonPath("$.showtimes[0].salesOpensAt").isString())
                .andExpect(jsonPath("$.showtimes[0].salesClosesAt").isString())
                .andExpect(jsonPath("$.showtimes[0].holdMinutes").value(10))
                .andExpect(jsonPath("$.showtimes[0].seatKind").value("GENERAL"))
                .andExpect(jsonPath("$.showtimes[0].salesStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.showtimes[0].remaining").value(2))
                .andExpect(jsonPath("$.showtimes[0].maxSeatsPerBuyer").value(4))
                .andExpect(jsonPath("$.serverTime").isString());
    }

    @Test
    @DisplayName("포스터가 없는 공연은 posterUrl을 null로, 회차가 없는 공연은 빈 회차 목록을 반환한다")
    void returnsNullPosterAndEmptyShowtimes() throws Exception {
        Performance performance = performanceRepository.save(Performance.create("포스터 없는 공연", "테스트 공연장"));

        mockMvc.perform(get("/api/performances/{performanceId}", performance.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasKey("posterUrl")))
                .andExpect(jsonPath("$.posterUrl").value(nullValue()))
                .andExpect(jsonPath("$.showtimes").isEmpty());
    }

    @Test
    @DisplayName("없는 공연을 조회하면 404를 반환한다")
    void returnsNotFoundForUnknownPerformance() throws Exception {
        mockMvc.perform(get("/api/performances/{performanceId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
