package com.openrun.reservation.booking.api;

import static com.openrun.reservation.TestLogin.holdHeaders;
import static com.openrun.reservation.TestLogin.loginAs;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class AvailabilityApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("로그인한 구매자에게 판매 상태, 남은 수, 1인 최대 좌석 수, 가진 좌석 수, 서버 시각을 반환한다")
    void returnsAvailabilityForBuyer() throws Exception {
        Showtime showtime = generalShowtime(3);
        mockMvc.perform(post("/api/orders").with(loginAs(1L)).with(holdHeaders(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showtimeId": %d, "quantity": 2, "buyerName": "밤산책"}
                                """.formatted(showtime.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/showtimes/{showtimeId}/availability", showtime.getId()).with(loginAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showtimeId").value(showtime.getId()))
                .andExpect(jsonPath("$.salesStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.remaining").value(1))
                .andExpect(jsonPath("$.maxSeatsPerBuyer").value(4))
                .andExpect(jsonPath("$.ownedCount").value(2))
                .andExpect(jsonPath("$.serverTime").isString());
        mockMvc.perform(get("/api/showtimes/{showtimeId}/availability", showtime.getId()).with(loginAs(2L)))
                .andExpect(jsonPath("$.ownedCount").value(0));
    }

    @Test
    @DisplayName("로그인하지 않고 구매 가능 수를 조회하면 401을 반환한다")
    void rejectsAnonymous() throws Exception {
        Showtime showtime = generalShowtime(1);

        mockMvc.perform(get("/api/showtimes/{showtimeId}/availability", showtime.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("없는 회차의 구매 가능 수를 조회하면 404를 반환한다")
    void returnsNotFoundForUnknownShowtime() throws Exception {
        mockMvc.perform(get("/api/showtimes/{showtimeId}/availability", Long.MAX_VALUE).with(loginAs(1L)))
                .andExpect(status().isNotFound());
    }

    private Showtime generalShowtime(int seats) {
        Performance performance = performanceRepository.save(Performance.create("구매 가능 수 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL, startsAt,
                startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, 4));
        for (int i = 1; i <= seats; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        return showtime;
    }
}
