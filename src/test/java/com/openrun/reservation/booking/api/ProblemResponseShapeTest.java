package com.openrun.reservation.booking.api;

import static com.openrun.reservation.TestLogin.loginAs;
import static com.openrun.reservation.TestLogin.publicIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class ProblemResponseShapeTest {

    private static final List<String> PROBLEM_KEYS = List.of("title", "status", "detail", "instance", "serverTime");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("없는 공연·회차·주문은 404와 대상별 detail을 주고 code는 주지 않는다")
    void notFoundProblems() throws Exception {
        assertProblem(perform(get("/api/performances/{id}", 999_999)), 404,
                "공연을 찾을 수 없습니다. performanceId=999999", null);
        assertProblem(perform(get("/api/showtimes/{id}/availability", 999_999).with(loginAs(1L))), 404,
                "회차를 찾을 수 없습니다. showtimeId=999999", null);
        assertProblem(perform(get("/api/orders/{token}", "no-such-token")), 404, "주문을 찾을 수 없습니다.", null);
    }

    @Test
    @DisplayName("선점 거부는 409와 code를 주고, 거부한 좌석이 있을 때만 seatIds를 준다")
    void seatHoldRejections() throws Exception {
        Showtime reserved = saveShowtime(SeatType.RESERVED, 4);
        Seat blocked = seatRepository.save(Seat.notForSale(reserved, SeatType.RESERVED, "A1"));

        Map<String, Object> withSeats = assertProblem(hold(1L, UUID.randomUUID().toString(), """
                {"showtimeId": %d, "seatIds": [%d], "buyerName": "밤산책"}
                """.formatted(reserved.getId(), blocked.getId())), 409, "선점할 수 없는 좌석이 있습니다.",
                "SEAT_NOT_AVAILABLE", "seatIds");
        assertThat(withSeats.get("seatIds")).isEqualTo(List.of(blocked.getId().intValue()));

        Showtime general = generalShowtime(3, 1);
        assertProblem(hold(1L, UUID.randomUUID().toString(), quantityBody(general, 2)), 409,
                "1인 최대 좌석 수(1석)를 넘습니다.", "SEAT_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("주문 거부는 409와 code를 준다")
    void orderRejection() throws Exception {
        Showtime general = generalShowtime(3, 4);
        String created = hold(1L, UUID.randomUUID().toString(), quantityBody(general, 1)).getContentAsString();
        String token = JsonPath.read(created, "$.token");
        perform(post("/api/orders/{token}/cancel", token).with(loginAs(1L)).with(csrf()));

        assertProblem(perform(post("/api/orders/{token}/confirm", token).with(loginAs(1L)).with(csrf())), 409,
                "취소한 주문은 확정할 수 없습니다.", "ORDER_CANCELLED");
    }

    @Test
    @DisplayName("같은 식별자로 다른 내용을 보내면 422와 IDEMPOTENCY_KEY_REUSED를 준다")
    void idempotencyKeyReused() throws Exception {
        Showtime general = generalShowtime(3, 4);
        String key = UUID.randomUUID().toString();
        hold(1L, key, quantityBody(general, 1));

        assertProblem(hold(1L, key, quantityBody(general, 2)), 422, "같은 요청 식별자로 다른 내용의 선점을 보냈습니다.",
                "IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    @DisplayName("좌석과 수량을 함께 보내거나 식별자가 UUID가 아니면 400과 detail을 주고 code는 주지 않는다")
    void badRequestProblems() throws Exception {
        Showtime general = generalShowtime(3, 4);

        assertProblem(hold(1L, UUID.randomUUID().toString(), """
                {"showtimeId": %d, "seatIds": [1], "quantity": 1, "buyerName": "밤산책"}
                """.formatted(general.getId())), 400, "좌석(seatIds)과 수량(quantity) 가운데 하나만 보내야 합니다.", null);
        assertProblem(hold(1L, "not-a-uuid", quantityBody(general, 1)), 400,
                "Idempotency-Key 헤더는 UUID 문자열이어야 합니다.", null);
    }

    private Map<String, Object> assertProblem(MockHttpServletResponse response, int status, String detail,
            String code, String... extraKeys) throws Exception {
        assertThat(response.getStatus()).isEqualTo(status);
        Map<String, Object> body = JsonPath.read(response.getContentAsString(), "$");
        List<String> keys = new ArrayList<>(PROBLEM_KEYS);
        if (code != null) {
            keys.add("code");
        }
        keys.addAll(List.of(extraKeys));
        assertThat(body).containsOnlyKeys(keys);
        assertThat(body.get("status")).isEqualTo(status);
        assertThat(body.get("detail")).isEqualTo(detail);
        assertThat(body.get("serverTime")).isInstanceOf(String.class);
        if (code != null) {
            assertThat(body.get("code")).isEqualTo(code);
        }
        return body;
    }

    private MockHttpServletResponse hold(Long buyerId, String key, String body) throws Exception {
        return perform(post("/api/orders").with(loginAs(buyerId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", key)
                .header("Expected-Buyer-Id", publicIdOf(buyerId))
                .content(body));
    }

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private String quantityBody(Showtime showtime, int quantity) {
        return """
                {"showtimeId": %d, "quantity": %d, "buyerName": "밤산책"}
                """.formatted(showtime.getId(), quantity);
    }

    private Showtime generalShowtime(int seats, int maxSeatsPerUser) {
        Showtime showtime = saveShowtime(SeatType.GENERAL, maxSeatsPerUser);
        for (int i = 1; i <= seats; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        return showtime;
    }

    private Showtime saveShowtime(SeatType seatType, int maxSeatsPerUser) {
        Performance performance = performanceRepository.save(Performance.create("실패 응답 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, seatType,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, maxSeatsPerUser));
    }
}
