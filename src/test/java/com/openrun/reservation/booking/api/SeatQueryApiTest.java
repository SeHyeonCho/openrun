package com.openrun.reservation.booking.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.booking.domain.Order;
import com.openrun.reservation.booking.domain.OrderRepository;
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
class SeatQueryApiTest {

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

    @Test
    @DisplayName("회차의 좌석을 등록한 순서대로 종류와 상태와 함께 조회한다")
    void returnsSeatsOfShowtime() throws Exception {
        Showtime showtime = createShowtime();
        Showtime other = createShowtime();
        seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1"));
        seatRepository.save(Seat.notForSale(showtime, SeatType.RESERVED, "A2"));
        seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, "1"));
        seatRepository.save(Seat.forSale(other, SeatType.RESERVED, "A1"));

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", showtime.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showtimeId").value(showtime.getId()))
                .andExpect(jsonPath("$.seats", hasSize(3)))
                .andExpect(jsonPath("$.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.seats[0].type").value("RESERVED"))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[1].label").value("A2"))
                .andExpect(jsonPath("$.seats[1].status").value("NOT_FOR_SALE"))
                .andExpect(jsonPath("$.seats[2].label").value("1"))
                .andExpect(jsonPath("$.seats[2].type").value("GENERAL"));
    }

    @Test
    @DisplayName("선점 기한이 지난 좌석은 선점 가능으로, 기한이 남은 좌석은 선점 중으로 보여 준다")
    void showsExpiredHoldAsAvailable() throws Exception {
        Showtime showtime = createShowtime();
        Seat expired = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1"));
        Seat active = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A2"));
        Instant longAgo = Instant.now().minus(Duration.ofHours(1));
        Instant justNow = Instant.now();
        expired.hold(orderRepository.save(Order.hold(showtime, 1L, longAgo)), longAgo);
        active.hold(orderRepository.save(Order.hold(showtime, 2L, justNow)), justNow);
        seatRepository.flush();

        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", showtime.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[1].label").value("A2"))
                .andExpect(jsonPath("$.seats[1].status").value("HELD"));
    }

    @Test
    @DisplayName("없는 회차의 좌석을 조회하면 404를 반환한다")
    void returnsNotFoundForUnknownShowtime() throws Exception {
        mockMvc.perform(get("/api/showtimes/{showtimeId}/seats", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private Showtime createShowtime() {
        Performance performance = performanceRepository.save(Performance.create("조회 테스트 공연"));
        Instant startsAt = Instant.now().plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, startsAt, startsAt.minus(Duration.ofDays(3)), 10, 4));
    }
}
