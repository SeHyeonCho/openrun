package com.openrun.reservation.common.web;

import static com.openrun.reservation.TestLogin.loginAs;
import static com.openrun.reservation.TestLogin.publicIdOf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.SeatType;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class ServerTimeProblemDetailTest {

    private static final String ISO_INSTANT = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?Z$";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Test
    @DisplayName("요청 형식이 틀린 400 응답에 서버 시각을 넣는다")
    void badRequestHasServerTime() throws Exception {
        expectServerTime(mockMvc.perform(post("/api/orders").with(loginAs(1L)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 400);
    }

    @Test
    @DisplayName("로그인하지 않은 401 응답과 CSRF 토큰이 없는 403 응답에 서버 시각을 넣는다")
    void securityFailuresHaveServerTime() throws Exception {
        expectServerTime(mockMvc.perform(get("/api/me")), 401);
        expectServerTime(mockMvc.perform(post("/api/orders").with(loginAs(1L))
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 403);
    }

    @Test
    @DisplayName("없는 공연의 404 응답에 서버 시각을 넣는다")
    void notFoundHasServerTime() throws Exception {
        expectServerTime(mockMvc.perform(get("/api/performances/{performanceId}", Long.MAX_VALUE)), 404);
    }

    @Test
    @DisplayName("선점 규칙 위반 409 응답과 식별자를 다른 내용으로 다시 쓴 422 응답에 code와 함께 서버 시각을 넣는다")
    void ruleViolationsHaveServerTime() throws Exception {
        Showtime showtime = reservedShowtime();
        String key = UUID.randomUUID().toString();

        expectServerTime(hold(key, showtime, Long.MAX_VALUE), 409)
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWTIME"));
        expectServerTime(hold(key, showtime, Long.MAX_VALUE - 1), 422)
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private ResultActions expectServerTime(ResultActions result, int status) throws Exception {
        return result
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.serverTime").value(org.hamcrest.Matchers.matchesPattern(ISO_INSTANT)));
    }

    private ResultActions hold(String key, Showtime showtime, Long seatId) throws Exception {
        return mockMvc.perform(post("/api/orders").with(loginAs(1L)).with(csrf())
                .header("Idempotency-Key", key)
                .header("Expected-Buyer-Id", publicIdOf(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"showtimeId": %d, "seatIds": [%d], "buyerName": "밤산책"}
                        """.formatted(showtime.getId(), seatId)));
    }

    private Showtime reservedShowtime() {
        Performance performance = performanceRepository.save(Performance.create("서버 시각 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, SeatType.RESERVED, startsAt,
                startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, 4));
    }
}
