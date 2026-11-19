package com.openrun.reservation.booking.api;

import static com.openrun.reservation.TestLogin.holdHeaders;
import static com.openrun.reservation.TestLogin.loginAs;
import static com.openrun.reservation.TestLogin.publicIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.booking.domain.Order;
import com.openrun.reservation.booking.domain.OrderRepository;
import com.openrun.reservation.booking.domain.OrderSeat;
import com.openrun.reservation.booking.domain.OrderSeatRepository;
import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.SeatType;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class OrderCancelApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderSeatRepository orderSeatRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("선점한 주문을 취소하면 200과 취소된 주문을 반환하고, 좌석은 바로 다시 팔 수 있으며 주문은 지우지 않는다")
    void cancelsHeldOrder() throws Exception {
        Showtime showtime = reservedShowtime(4);
        Long first = saveSeat(showtime, "A1").getId();
        Long second = saveSeat(showtime, "A2").getId();
        String token = holdSeats(showtime, 1L, List.of(first, second));

        cancelOrder(token, 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.entryNumbers").isEmpty())
                .andExpect(jsonPath("$.isOwner").value(true));

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", showtime.getId()))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[1].status").value("AVAILABLE"));
        mockMvc.perform(get("/api/orders/{token}", token)).andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get("/api/me/orders").with(loginAs(1L)))
                .andExpect(jsonPath("$.orders[0].token").value(token))
                .andExpect(jsonPath("$.orders[0].status").value("CANCELLED"));
        assertThat(count("SELECT COUNT(*) FROM orders WHERE token = '" + token + "'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM order_seat")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE current_order_id IS NOT NULL OR held_until IS NOT NULL"))
                .isZero();
    }

    @Test
    @DisplayName("이미 취소한 주문을 다시 취소하면 서버 시각 말고는 같은 응답을 반환하고 아무것도 바꾸지 않는다")
    void cancellingTwiceReturnsSameResult() throws Exception {
        Showtime showtime = reservedShowtime(4);
        Long seatId = saveSeat(showtime, "A1").getId();
        String token = holdSeats(showtime, 1L, List.of(seatId));

        String firstResponse = cancelOrder(token, 1L).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondResponse = cancelOrder(token, 1L).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(withoutServerTime(secondResponse)).isEqualTo(withoutServerTime(firstResponse));
        assertThat(seatStatus(seatId)).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("다른 구매자가 취소된 좌석을 다시 잡은 뒤에 원래 구매자가 다시 취소해도 그 좌석은 그대로다")
    void cancellingAgainDoesNotReleaseSeatHeldByAnotherOrder() throws Exception {
        Showtime showtime = reservedShowtime(4);
        Long seatId = saveSeat(showtime, "A1").getId();
        String token = holdSeats(showtime, 1L, List.of(seatId));
        cancelOrder(token, 1L).andExpect(status().isOk());
        String other = holdSeats(showtime, 2L, List.of(seatId));

        cancelOrder(token, 1L).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(seatStatus(seatId)).isEqualTo("HELD");
        orderRepository.flush();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT o.token FROM seat s JOIN orders o ON o.id = s.current_order_id WHERE s.id = ?",
                String.class, seatId)).isEqualTo(other);
    }

    @Test
    @DisplayName("로그인하지 않거나 CSRF 토큰 없이 취소하면 401·403을 반환하고 주문은 선점 상태로 남는다")
    void rejectsAnonymousOrMissingCsrf() throws Exception {
        Showtime showtime = reservedShowtime(4);
        Long seatId = saveSeat(showtime, "A1").getId();
        String token = holdSeats(showtime, 1L, List.of(seatId));

        mockMvc.perform(post("/api/orders/{token}/cancel", token).with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/{token}/cancel", token).with(loginAs(1L)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/orders/{token}", token)).andExpect(jsonPath("$.status").value("HELD"));
        assertThat(seatStatus(seatId)).isEqualTo("HELD");
    }

    @Test
    @DisplayName("다른 구매자의 주문이나 없는 주문을 취소하면 404를 반환하고 주문과 좌석은 선점 상태로 남는다")
    void hidesOrderOfAnotherBuyer() throws Exception {
        Showtime showtime = reservedShowtime(4);
        Long seatId = saveSeat(showtime, "A1").getId();
        String token = holdSeats(showtime, 1L, List.of(seatId));

        cancelOrder(token, 2L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").doesNotExist());
        cancelOrder("o_unknown", 1L).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/orders/{token}", token)).andExpect(jsonPath("$.status").value("HELD"));
        assertThat(seatStatus(seatId)).isEqualTo("HELD");
    }

    @Test
    @DisplayName("확정한 주문을 취소하면 ORDER_CONFIRMED로 거부하고 주문은 확정, 좌석은 판매 완료로 남는다")
    void rejectsConfirmedOrder() throws Exception {
        Showtime showtime = reservedShowtime(4);
        Long seatId = saveSeat(showtime, "A1").getId();
        String token = holdSeats(showtime, 1L, List.of(seatId));
        mockMvc.perform(post("/api/orders/{token}/confirm", token).with(loginAs(1L)).with(csrf()))
                .andExpect(status().isOk());

        cancelOrder(token, 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_CONFIRMED"));

        mockMvc.perform(get("/api/orders/{token}", token)).andExpect(jsonPath("$.status").value("CONFIRMED"));
        assertThat(seatStatus(seatId)).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("선점 기한이 지난 주문과 만료 처리된 주문을 취소하면 ORDER_EXPIRED로 거부하고 상태를 바꾸지 않는다")
    void rejectsExpiredOrder() throws Exception {
        Showtime showtime = reservedShowtime(4);
        Seat pastSeat = saveSeat(showtime, "A1");
        Order pastHeldUntil = holdInPast(showtime, pastSeat);
        Seat expiredSeat = saveSeat(showtime, "A2");
        Order expired = holdInPast(showtime, expiredSeat);
        expired.expire();
        orderRepository.flush();

        cancelOrder(pastHeldUntil.getToken(), 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));
        cancelOrder(expired.getToken(), 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));

        assertThat(pastHeldUntil.getStatus().name()).isEqualTo("HELD");
        assertThat(pastSeat.getStatus().name()).isEqualTo("HELD");
        assertThat(expired.getStatus().name()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("취소한 주문의 좌석은 1인 최대 좌석 수에서 빠져, 같은 구매자가 다른 좌석을 한도까지 다시 선점할 수 있다")
    void cancelledSeatsLeaveSeatLimit() throws Exception {
        Showtime showtime = reservedShowtime(2);
        Long first = saveSeat(showtime, "A1").getId();
        Long second = saveSeat(showtime, "A2").getId();
        Long third = saveSeat(showtime, "A3").getId();
        Long fourth = saveSeat(showtime, "A4").getId();
        String token = holdSeats(showtime, 1L, List.of(first, second));
        createOrder(showtime, 1L, List.of(third))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_EXCEEDED"));

        cancelOrder(token, 1L).andExpect(status().isOk());

        createOrder(showtime, 1L, List.of(third, fourth)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("자유석 주문을 취소하면 남은 수로 돌아가 다른 구매자가 그 수만큼 선점할 수 있다")
    void cancelledGeneralSeatsReturnToRemaining() throws Exception {
        Showtime showtime = saveShowtime(SeatType.GENERAL, 4);
        seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, "1"));
        seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, "2"));
        String token = JsonPath.read(holdQuantity(showtime, 1L, 2).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.token");
        holdQuantity(showtime, 2L, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"));

        cancelOrder(token, 1L).andExpect(status().isOk()).andExpect(jsonPath("$.entryNumbers").isEmpty());

        holdQuantity(showtime, 2L, 2).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("취소한 주문을 만든 선점 요청을 같은 식별자로 다시 보내면 그 주문을 CANCELLED로 돌려준다")
    void replayedHoldShowsCancelledOrder() throws Exception {
        Showtime showtime = reservedShowtime(4);
        Long seatId = saveSeat(showtime, "A1").getId();
        String key = UUID.randomUUID().toString();
        String token = JsonPath.read(holdWithKey(showtime, key, seatId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.token");
        cancelOrder(token, 1L).andExpect(status().isOk());

        holdWithKey(showtime, key, seatId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(seatStatus(seatId)).isEqualTo("AVAILABLE");
    }

    private Showtime reservedShowtime(int maxSeatsPerUser) {
        return saveShowtime(SeatType.RESERVED, maxSeatsPerUser);
    }

    private Showtime saveShowtime(SeatType seatType, int maxSeatsPerUser) {
        Performance performance = performanceRepository.save(Performance.create("취소 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, seatType,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, maxSeatsPerUser));
    }

    private Seat saveSeat(Showtime showtime, String label) {
        return seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, label));
    }

    private Order holdInPast(Showtime showtime, Seat seat) {
        Instant longAgo = Instant.now().minus(Duration.ofHours(1));
        Order order = orderRepository.save(Order.hold(showtime, 1L, "밤산책", longAgo));
        seat.hold(order, longAgo);
        orderSeatRepository.save(OrderSeat.of(order, seat));
        orderSeatRepository.flush();
        return order;
    }

    private String holdSeats(Showtime showtime, Long buyerId, List<Long> seatIds) throws Exception {
        String response = createOrder(showtime, buyerId, seatIds).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private ResultActions createOrder(Showtime showtime, Long buyerId, List<Long> seatIds) throws Exception {
        String seats = seatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return mockMvc.perform(post("/api/orders").with(loginAs(buyerId)).with(holdHeaders(buyerId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"showtimeId": %d, "seatIds": [%s], "buyerName": "밤산책"}
                        """.formatted(showtime.getId(), seats)));
    }

    private ResultActions holdQuantity(Showtime showtime, Long buyerId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/orders").with(loginAs(buyerId)).with(holdHeaders(buyerId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"showtimeId": %d, "quantity": %d, "buyerName": "밤산책"}
                        """.formatted(showtime.getId(), quantity)));
    }

    private ResultActions holdWithKey(Showtime showtime, String key, Long seatId) throws Exception {
        return mockMvc.perform(post("/api/orders").with(loginAs(1L)).with(csrf())
                .header("Idempotency-Key", key)
                .header("Expected-Buyer-Id", publicIdOf(1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"showtimeId": %d, "seatIds": [%d], "buyerName": "밤산책"}
                        """.formatted(showtime.getId(), seatId)));
    }

    private ResultActions cancelOrder(String token, Long buyerId) throws Exception {
        return mockMvc.perform(post("/api/orders/{token}/cancel", token).with(loginAs(buyerId)).with(csrf()));
    }

    private String seatStatus(Long seatId) {
        orderRepository.flush();
        return jdbcTemplate.queryForObject("SELECT status FROM seat WHERE id = ?", String.class, seatId);
    }

    private int count(String sql) {
        orderRepository.flush();
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }

    private String withoutServerTime(String response) {
        return response.replaceAll("\"serverTime\":\"[^\"]*\"", "");
    }
}
