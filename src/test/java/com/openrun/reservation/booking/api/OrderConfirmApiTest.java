package com.openrun.reservation.booking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import java.util.stream.Collectors;
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
class OrderConfirmApiTest {

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
    @DisplayName("선점한 주문을 확정하면 200과 확정된 주문을 반환하고 좌석은 판매 완료가 된다")
    void confirmsHeldOrder() throws Exception {
        Showtime showtime = openShowtime();
        Long first = saveSeat(showtime, "A1").getId();
        Long second = saveSeat(showtime, "A2").getId();
        Long orderId = holdOrder(showtime.getId(), 1L, List.of(first, second));

        confirmOrder(orderId, 1L)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.showtimeId").value(showtime.getId()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.seatIds", contains(first.intValue(), second.intValue())));

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", showtime.getId()))
                .andExpect(jsonPath("$.seats[0].status").value("SOLD"))
                .andExpect(jsonPath("$.seats[1].status").value("SOLD"));
    }

    @Test
    @DisplayName("이미 확정한 주문을 다시 확정하면 같은 응답을 반환하고 상태는 그대로다")
    void confirmingTwiceReturnsSameResult() throws Exception {
        Showtime showtime = openShowtime();
        Long seatId = saveSeat(showtime, "A1").getId();
        Long orderId = holdOrder(showtime.getId(), 1L, List.of(seatId));

        String firstResponse = confirmOrder(orderId, 1L).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondResponse = confirmOrder(orderId, 1L).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(secondResponse).isEqualTo(firstResponse);
        assertThat(seatRepository.findById(seatId).orElseThrow().getStatus().name()).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("판매 완료된 좌석을 다른 구매자가 선점하면 SEAT_NOT_AVAILABLE로 거부한다")
    void rejectsHoldingSoldSeat() throws Exception {
        Showtime showtime = openShowtime();
        Long seatId = saveSeat(showtime, "A1").getId();
        confirmOrder(holdOrder(showtime.getId(), 1L, List.of(seatId)), 1L).andExpect(status().isOk());

        createOrder(showtime.getId(), 2L, List.of(seatId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("선점 기한이 지난 주문을 확정하면 ORDER_EXPIRED로 거부하고 상태를 바꾸지 않는다")
    void rejectsOrderPastHeldUntil() throws Exception {
        Showtime showtime = openShowtime();
        Seat seat = saveSeat(showtime, "A1");
        Order order = holdInPast(showtime, seat);

        confirmOrder(order.getId(), 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));

        assertThat(order.getStatus().name()).isEqualTo("HELD");
        assertThat(seat.getStatus().name()).isEqualTo("HELD");
    }

    @Test
    @DisplayName("만료 처리된 주문을 확정하면 ORDER_EXPIRED로 거부한다")
    void rejectsExpiredOrder() throws Exception {
        Showtime showtime = openShowtime();
        Seat seat = saveSeat(showtime, "A1");
        Order order = holdInPast(showtime, seat);
        order.expire();
        orderRepository.flush();

        confirmOrder(order.getId(), 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));
    }

    @Test
    @DisplayName("다른 구매자의 주문을 확정하면 403 ORDER_NOT_OWNED로 거부한다")
    void rejectsOrderOfAnotherBuyer() throws Exception {
        Showtime showtime = openShowtime();
        Long orderId = holdOrder(showtime.getId(), 1L, List.of(saveSeat(showtime, "A1").getId()));

        confirmOrder(orderId, 2L)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OWNED"));
    }

    @Test
    @DisplayName("없는 주문을 확정하면 404를 반환한다")
    void returnsNotFoundForUnknownOrder() throws Exception {
        confirmOrder(Long.MAX_VALUE, 1L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    @DisplayName("구매자 없이 확정을 요청하면 400을 반환한다")
    void rejectsMissingUserId() throws Exception {
        mockMvc.perform(post("/api/orders/{orderId}/confirm", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private Showtime openShowtime() {
        Performance performance = performanceRepository.save(Performance.create("확정 테스트 공연"));
        Instant now = Instant.now();
        return showtimeRepository.save(Showtime.create(performance, now.plus(Duration.ofDays(7)),
                now.minus(Duration.ofHours(1)), 10, 4));
    }

    private Seat saveSeat(Showtime showtime, String label) {
        return seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, label));
    }

    private Order holdInPast(Showtime showtime, Seat seat) {
        Instant longAgo = Instant.now().minus(Duration.ofHours(1));
        Order order = orderRepository.save(Order.hold(showtime, 1L, longAgo));
        seat.hold(order, longAgo);
        orderSeatRepository.save(OrderSeat.of(order, seat));
        orderSeatRepository.flush();
        return order;
    }

    private Long holdOrder(Long showtimeId, Long userId, List<Long> seatIds) throws Exception {
        String response = createOrder(showtimeId, userId, seatIds).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.orderId")).longValue();
    }

    private ResultActions createOrder(Long showtimeId, Long userId, List<Long> seatIds) throws Exception {
        String seats = seatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"showtimeId": %d, "userId": %d, "seatIds": [%s]}
                        """.formatted(showtimeId, userId, seats)));
    }

    private ResultActions confirmOrder(Long orderId, Long userId) throws Exception {
        return mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userId": %d}
                        """.formatted(userId)));
    }
}
