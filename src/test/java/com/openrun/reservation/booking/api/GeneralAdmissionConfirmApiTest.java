package com.openrun.reservation.booking.api;

import static com.openrun.reservation.TestLogin.holdHeaders;
import static com.openrun.reservation.TestLogin.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class GeneralAdmissionConfirmApiTest {

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
    @DisplayName("자유석 주문을 확정하면 회차별로 1부터 이어진 입장번호를 받고, 조회와 내 주문에도 같은 번호가 나온다")
    void assignsEntryNumbersOnConfirm() throws Exception {
        Showtime showtime = generalShowtime(5);
        String first = hold(showtime, 1L, 2);
        String second = hold(showtime, 2L, 1);

        confirm(second, 2L).andExpect(status().isOk()).andExpect(jsonPath("$.entryNumbers", contains(1)));
        confirm(first, 1L).andExpect(status().isOk()).andExpect(jsonPath("$.entryNumbers", contains(2, 3)));

        mockMvc.perform(get("/api/orders/{token}", first)).andExpect(jsonPath("$.entryNumbers", contains(2, 3)));
        mockMvc.perform(get("/api/me/orders").with(loginAs(1L)))
                .andExpect(jsonPath("$.orders[0].entryNumbers", contains(2, 3)));
    }

    @Test
    @DisplayName("선점 중인 자유석 주문은 입장번호가 없고, 이미 확정한 주문을 다시 확정하면 처음 받은 번호를 그대로 준다")
    void keepsNumbersOnReconfirmAndHasNoneWhileHeld() throws Exception {
        Showtime showtime = generalShowtime(3);
        String token = hold(showtime, 1L, 2);

        mockMvc.perform(get("/api/orders/{token}", token)).andExpect(jsonPath("$.entryNumbers").isEmpty());
        confirm(token, 1L).andExpect(jsonPath("$.entryNumbers", contains(1, 2)));
        confirm(token, 1L).andExpect(status().isOk()).andExpect(jsonPath("$.entryNumbers", contains(1, 2)));

        assertThat(count("SELECT COUNT(*) FROM order_entry_number")).isEqualTo(2);
    }

    @Test
    @DisplayName("확정에 실패한 주문은 입장번호를 쓰지 않아서, 다음에 확정한 주문이 1번부터 받는다")
    void failedConfirmDoesNotUseNumbers() throws Exception {
        Showtime showtime = generalShowtime(3);
        Order expired = holdInPast(showtime, 9L);
        String token = hold(showtime, 1L, 1);

        confirm(expired.getToken(), 9L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_EXPIRED"));
        confirm(token, 1L).andExpect(jsonPath("$.entryNumbers", contains(1)));

        assertThat(count("SELECT COUNT(*) FROM order_entry_number WHERE order_id = " + expired.getId())).isZero();
    }

    @Test
    @DisplayName("지정석 주문을 확정하면 입장번호를 주지 않는다")
    void reservedOrderHasNoEntryNumbers() throws Exception {
        Performance performance = performanceRepository.save(Performance.create("지정석 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, SeatType.RESERVED,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, 4));
        Long seatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        String response = mockMvc.perform(post("/api/orders").with(loginAs(1L)).with(holdHeaders(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showtimeId": %d, "seatIds": [%d], "buyerName": "밤산책"}
                                """.formatted(showtime.getId(), seatId)))
                .andReturn().getResponse().getContentAsString();

        confirm(JsonPath.read(response, "$.token"), 1L).andExpect(jsonPath("$.entryNumbers").isEmpty());
        assertThat(count("SELECT COUNT(*) FROM showtime_entry_sequence")).isZero();
    }

    private String hold(Showtime showtime, Long buyerId, int quantity) throws Exception {
        String response = mockMvc.perform(post("/api/orders").with(loginAs(buyerId))
                .with(holdHeaders(buyerId)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showtimeId": %d, "quantity": %d, "buyerName": "밤산책"}
                                """.formatted(showtime.getId(), quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    private ResultActions confirm(String token, Long buyerId) throws Exception {
        return mockMvc.perform(post("/api/orders/{token}/confirm", token).with(loginAs(buyerId)).with(csrf()));
    }

    private Order holdInPast(Showtime showtime, Long buyerId) {
        Seat seat = seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, "past-" + buyerId));
        Instant longAgo = Instant.now().minus(Duration.ofHours(1));
        Order order = orderRepository.save(Order.hold(showtime, buyerId, "밤산책", longAgo));
        seat.hold(order, longAgo);
        orderSeatRepository.saveAndFlush(OrderSeat.of(order, seat));
        return order;
    }

    private Showtime generalShowtime(int seats) {
        Performance performance = performanceRepository.save(Performance.create("자유석 확정 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, SeatType.GENERAL,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, 4));
        for (int i = 1; i <= seats; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        return showtime;
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
