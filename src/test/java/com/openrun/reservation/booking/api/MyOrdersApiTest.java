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
class MyOrdersApiTest {

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
    @DisplayName("내 주문은 로그인한 구매자의 주문만 선점한 시각의 최근 순으로 반환한다")
    void returnsOnlyMyOrdersNewestFirst() throws Exception {
        Showtime first = openShowtime("첫 공연");
        Showtime second = openShowtime("둘째 공연");
        String older = holdOrder(first, 1L, "A1", "A2");
        holdOrder(first, 2L, "B1");
        String newer = holdOrder(second, 1L, "A1");

        mockMvc.perform(get("/api/me/orders").with(loginAs(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders.length()").value(2))
                .andExpect(jsonPath("$.orders[0].token").value(newer))
                .andExpect(jsonPath("$.orders[0].performance.title").value("둘째 공연"))
                .andExpect(jsonPath("$.orders[0].showtime.id").value(second.getId()))
                .andExpect(jsonPath("$.orders[0].quantity").value(1))
                .andExpect(jsonPath("$.orders[1].token").value(older))
                .andExpect(jsonPath("$.orders[1].status").value("HELD"))
                .andExpect(jsonPath("$.orders[1].quantity").value(2))
                .andExpect(jsonPath("$.orders[1].entryNumbers").isEmpty())
                .andExpect(jsonPath("$.orders[1].heldUntil").isString())
                .andExpect(jsonPath("$.orders[1].createdAt").isString())
                .andExpect(jsonPath("$.orders[1].buyerName").doesNotExist())
                .andExpect(jsonPath("$.orders[1].isOwner").doesNotExist())
                .andExpect(jsonPath("$.orders[1].serverTime").doesNotExist())
                .andExpect(jsonPath("$.serverTime").isString());
    }

    @Test
    @DisplayName("선점 기한이 지난 내 주문은 EXPIRED로 보여 주고, 주문이 없으면 빈 목록을 반환한다")
    void showsExpiredOrdersAndEmptyList() throws Exception {
        Showtime showtime = openShowtime("만료 공연");
        Seat seat = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1"));
        Instant longAgo = Instant.now().minus(Duration.ofHours(1));
        Order order = orderRepository.save(Order.hold(showtime, 1L, "밤산책", longAgo));
        seat.hold(order, longAgo);
        orderSeatRepository.saveAndFlush(OrderSeat.of(order, seat));

        mockMvc.perform(get("/api/me/orders").with(loginAs(1L)))
                .andExpect(jsonPath("$.orders[0].token").value(order.getToken()))
                .andExpect(jsonPath("$.orders[0].status").value("EXPIRED"));
        mockMvc.perform(get("/api/me/orders").with(loginAs(3L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders").isEmpty());
    }

    @Test
    @DisplayName("로그인하지 않고 내 주문을 요청하면 401을 반환한다")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/me/orders")).andExpect(status().isUnauthorized());
    }

    private String holdOrder(Showtime showtime, Long buyerId, String... labels) throws Exception {
        StringBuilder seatIds = new StringBuilder();
        for (String label : labels) {
            if (!seatIds.isEmpty()) {
                seatIds.append(',');
            }
            seatIds.append(seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, label)).getId());
        }
        String response = mockMvc.perform(post("/api/orders").with(loginAs(buyerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showtimeId": %d, "seatIds": [%s], "buyerName": "밤산책"}
                                """.formatted(showtime.getId(), seatIds)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private Showtime openShowtime(String title) {
        Performance performance = performanceRepository.save(Performance.create(title, "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, startsAt, startsAt.plus(Duration.ofHours(2)),
                now.minus(Duration.ofHours(1)), 10, 4));
    }
}
