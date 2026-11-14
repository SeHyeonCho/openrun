package com.openrun.reservation.booking.api;

import static com.openrun.reservation.TestLogin.loginAs;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
class OrderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("좌석을 선점하면 주문을 만들고 201과 선점 기한, 좌석 ID 순으로 정렬한 좌석을 반환한다")
    void createsOrderHoldingSeats() throws Exception {
        Showtime showtime = openShowtime(4);
        Long first = saveSeat(showtime, "A1").getId();
        Long second = saveSeat(showtime, "A2").getId();

        createOrder(showtime.getId(), 1L, List.of(second, first))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNumber())
                .andExpect(jsonPath("$.showtimeId").value(showtime.getId()))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.heldUntil").isString())
                .andExpect(jsonPath("$.seatIds", contains(first.intValue(), second.intValue())));
    }

    @Test
    @DisplayName("판매 시작 전에는 SALES_NOT_OPEN으로 거부한다")
    void rejectsBeforeSalesOpen() throws Exception {
        Performance performance = performanceRepository.save(Performance.create("판매 전 공연"));
        Instant now = Instant.now();
        Showtime showtime = showtimeRepository.save(Showtime.create(performance, now.plus(Duration.ofDays(7)),
                now.plus(Duration.ofDays(1)), 10, 4));
        Long seat = saveSeat(showtime, "A1").getId();

        createOrder(showtime.getId(), 1L, List.of(seat))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SALES_NOT_OPEN"));
    }

    @Test
    @DisplayName("1인 최대 좌석 수를 넘으면 SEAT_LIMIT_EXCEEDED로 거부한다")
    void rejectsOverSeatLimit() throws Exception {
        Showtime showtime = openShowtime(2);
        List<Long> seats = List.of(saveSeat(showtime, "A1").getId(), saveSeat(showtime, "A2").getId(),
                saveSeat(showtime, "A3").getId());

        createOrder(showtime.getId(), 1L, seats)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("다른 회차의 좌석이나 없는 좌석은 SEAT_NOT_IN_SHOWTIME으로 거부하고 해당 좌석을 알려준다")
    void rejectsSeatsOutsideShowtime() throws Exception {
        Showtime showtime = openShowtime(4);
        Showtime other = openShowtime(4);
        Long own = saveSeat(showtime, "A1").getId();
        Long foreign = saveSeat(other, "A1").getId();
        Long missing = Long.MAX_VALUE;

        createOrder(showtime.getId(), 1L, List.of(own, foreign, missing))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWTIME"))
                .andExpect(jsonPath("$.seatIds", contains(foreign.intValue(), missing)));
    }

    @Test
    @DisplayName("판매하지 않는 좌석은 SEAT_NOT_AVAILABLE로 거부하고 해당 좌석을 알려준다")
    void rejectsSeatNotForSale() throws Exception {
        Showtime showtime = openShowtime(4);
        Long available = saveSeat(showtime, "A1").getId();
        Long onsite = seatRepository.save(Seat.notForSale(showtime, SeatType.RESERVED, "A2")).getId();

        createOrder(showtime.getId(), 1L, List.of(available, onsite))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.seatIds", contains(onsite.intValue())));
    }

    @Test
    @DisplayName("이미 선점된 좌석은 SEAT_NOT_AVAILABLE로 거부한다")
    void rejectsSeatAlreadyHeld() throws Exception {
        Showtime showtime = openShowtime(4);
        Long seat = saveSeat(showtime, "A1").getId();
        createOrder(showtime.getId(), 1L, List.of(seat)).andExpect(status().isCreated());

        createOrder(showtime.getId(), 2L, List.of(seat))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("없는 회차는 404를 반환한다")
    void returnsNotFoundForUnknownShowtime() throws Exception {
        createOrder(Long.MAX_VALUE, 1L, List.of(1L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("좌석 목록이 비었거나 같은 좌석이 중복되면 400을 반환한다")
    void rejectsInvalidSeatList() throws Exception {
        Showtime showtime = openShowtime(4);
        Long seat = saveSeat(showtime, "A1").getId();

        createOrder(showtime.getId(), 1L, List.of()).andExpect(status().isBadRequest());
        createOrder(showtime.getId(), 1L, List.of(seat, seat)).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회차가 빠진 선점 요청은 400을 반환한다")
    void rejectsMissingShowtime() throws Exception {
        mockMvc.perform(post("/api/orders").with(loginAs(1L)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seatIds\": [1]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("1인 최대 좌석 수는 회차마다 따로 센다")
    void countsSeatLimitPerShowtime() throws Exception {
        Showtime showtime = openShowtime(1);
        Showtime other = openShowtime(1);
        createOrder(showtime.getId(), 1L, List.of(saveSeat(showtime, "A1").getId())).andExpect(status().isCreated());

        createOrder(other.getId(), 1L, List.of(saveSeat(other, "A1").getId())).andExpect(status().isCreated());
        createOrder(showtime.getId(), 1L, List.of(saveSeat(showtime, "A2").getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_EXCEEDED"));
    }

    private ResultActions createOrder(Long showtimeId, Long buyerId, List<Long> seatIds) throws Exception {
        String seats = seatIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String body = """
                {"showtimeId": %d, "seatIds": [%s]}
                """.formatted(showtimeId, seats);
        return mockMvc.perform(post("/api/orders").with(loginAs(buyerId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Showtime openShowtime(int maxSeatsPerUser) {
        Performance performance = performanceRepository.save(Performance.create("선점 API 테스트 공연"));
        Instant now = Instant.now();
        return showtimeRepository.save(Showtime.create(performance, now.plus(Duration.ofDays(7)),
                now.minus(Duration.ofHours(1)), 10, maxSeatsPerUser));
    }

    private Seat saveSeat(Showtime showtime, String label) {
        return seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, label));
    }
}
