package com.openrun.reservation.booking.api;

import static com.openrun.reservation.TestLogin.loginAs;
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
class OrderQueryApiTest {

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

    @Test
    @DisplayName("주문 토큰이 있으면 로그인하지 않아도 주문을 볼 수 있고, 주문을 만든 구매자일 때만 isOwner가 true다")
    void anyoneWithTokenCanViewOrder() throws Exception {
        Showtime showtime = openShowtime();
        String token = holdOrder(showtime, 1L);

        mockMvc.perform(get("/api/orders/{token}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.quantity").value(1))
                .andExpect(jsonPath("$.buyerName").value("밤산책"))
                .andExpect(jsonPath("$.isOwner").value(false))
                .andExpect(jsonPath("$.serverTime").isString());
        mockMvc.perform(get("/api/orders/{token}", token).with(loginAs(1L)))
                .andExpect(jsonPath("$.isOwner").value(true));
        mockMvc.perform(get("/api/orders/{token}", token).with(loginAs(2L)))
                .andExpect(jsonPath("$.isOwner").value(false));
    }

    @Test
    @DisplayName("선점 기한이 지난 주문은 저장된 상태가 HELD여도 EXPIRED로 보여 준다")
    void showsHeldOrderPastHeldUntilAsExpired() throws Exception {
        Showtime showtime = openShowtime();
        Seat seat = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1"));
        Instant longAgo = Instant.now().minus(Duration.ofHours(1));
        Order order = orderRepository.save(Order.hold(showtime, 1L, "밤산책", longAgo));
        seat.hold(order, longAgo);
        orderSeatRepository.saveAndFlush(OrderSeat.of(order, seat));

        mockMvc.perform(get("/api/orders/{token}", order.getToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    @DisplayName("없는 주문 토큰은 404를 반환한다")
    void returnsNotFoundForUnknownToken() throws Exception {
        mockMvc.perform(get("/api/orders/{token}", "o_unknown"))
                .andExpect(status().isNotFound());
    }

    private String holdOrder(Showtime showtime, Long buyerId) throws Exception {
        Long seatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        String response = mockMvc.perform(post("/api/orders").with(loginAs(buyerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showtimeId": %d, "seatIds": [%d], "buyerName": "밤산책"}
                                """.formatted(showtime.getId(), seatId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private Showtime openShowtime() {
        Performance performance = performanceRepository.save(Performance.create("조회 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, startsAt, startsAt.plus(Duration.ofHours(2)),
                now.minus(Duration.ofHours(1)), 10, 4));
    }
}
