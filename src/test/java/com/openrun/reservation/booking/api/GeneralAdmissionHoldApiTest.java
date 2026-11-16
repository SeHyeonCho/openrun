package com.openrun.reservation.booking.api;

import static com.openrun.reservation.TestLogin.holdHeaders;
import static com.openrun.reservation.TestLogin.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.SeatStatus;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class GeneralAdmissionHoldApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    @DisplayName("자유석 회차는 수량으로 선점하고, 남은 좌석 가운데 그 수만큼 주문에 잡힌다")
    void holdsGeneralSeatsByQuantity() throws Exception {
        Showtime showtime = generalShowtime(4);
        saveGeneralSeats(showtime, 5);

        hold(1L, """
                {"showtimeId": %d, "quantity": 2, "buyerName": "밤산책"}
                """.formatted(showtime.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.entryNumbers").isEmpty());

        assertThat(seatsWithStatus(showtime, SeatStatus.HELD)).isEqualTo(2);
    }

    @Test
    @DisplayName("남은 좌석보다 많은 수량은 SEAT_NOT_AVAILABLE로 거부하고 아무 좌석도 잡지 않는다. 판매하지 않는 좌석은 남은 수에 들지 않는다")
    void rejectsQuantityOverRemaining() throws Exception {
        Showtime showtime = generalShowtime(4);
        saveGeneralSeats(showtime, 2);
        seatRepository.save(Seat.notForSale(showtime, SeatType.GENERAL, "onsite-1"));

        hold(1L, """
                {"showtimeId": %d, "quantity": 3, "buyerName": "밤산책"}
                """.formatted(showtime.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"));

        assertThat(seatsWithStatus(showtime, SeatStatus.HELD)).isZero();
    }

    @Test
    @DisplayName("자유석도 1인 최대 좌석 수를 넘으면 SEAT_LIMIT_EXCEEDED로 거부한다")
    void rejectsQuantityOverSeatLimit() throws Exception {
        Showtime showtime = generalShowtime(2);
        saveGeneralSeats(showtime, 5);
        hold(1L, """
                {"showtimeId": %d, "quantity": 2, "buyerName": "밤산책"}
                """.formatted(showtime.getId())).andExpect(status().isCreated());

        hold(1L, """
                {"showtimeId": %d, "quantity": 1, "buyerName": "밤산책"}
                """.formatted(showtime.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("회차의 좌석 종류와 맞지 않는 쪽을 보내거나, 둘 다 보내거나, 둘 다 없으면 400을 반환한다")
    void rejectsSeatChoiceNotMatchingShowtime() throws Exception {
        Showtime general = generalShowtime(4);
        Long generalSeat = saveGeneralSeats(general, 1);
        Showtime reserved = reservedShowtime();
        Long reservedSeat = seatRepository.save(Seat.forSale(reserved, SeatType.RESERVED, "A1")).getId();

        hold(1L, """
                {"showtimeId": %d, "seatIds": [%d], "buyerName": "밤산책"}
                """.formatted(general.getId(), generalSeat)).andExpect(status().isBadRequest());
        hold(1L, """
                {"showtimeId": %d, "quantity": 1, "buyerName": "밤산책"}
                """.formatted(reserved.getId())).andExpect(status().isBadRequest());
        hold(1L, """
                {"showtimeId": %d, "seatIds": [%d], "quantity": 1, "buyerName": "밤산책"}
                """.formatted(reserved.getId(), reservedSeat)).andExpect(status().isBadRequest());
        hold(1L, """
                {"showtimeId": %d, "buyerName": "밤산책"}
                """.formatted(general.getId())).andExpect(status().isBadRequest());

        assertThat(seatsWithStatus(general, SeatStatus.HELD)).isZero();
        assertThat(seatsWithStatus(reserved, SeatStatus.HELD)).isZero();
    }

    @Test
    @DisplayName("수량이 0 이하이거나 정수가 아니면 400을 반환한다")
    void rejectsInvalidQuantity() throws Exception {
        Showtime showtime = generalShowtime(4);
        saveGeneralSeats(showtime, 3);

        for (String quantity : new String[] {"0", "-1", "1.5"}) {
            hold(1L, """
                    {"showtimeId": %d, "quantity": %s, "buyerName": "밤산책"}
                    """.formatted(showtime.getId(), quantity)).andExpect(status().isBadRequest());
        }
        assertThat(seatsWithStatus(showtime, SeatStatus.HELD)).isZero();
    }

    private ResultActions hold(Long buyerId, String body) throws Exception {
        return mockMvc.perform(post("/api/orders").with(loginAs(buyerId)).with(holdHeaders(buyerId)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Showtime generalShowtime(int maxSeatsPerUser) {
        return saveShowtime(SeatType.GENERAL, maxSeatsPerUser);
    }

    private Showtime reservedShowtime() {
        return saveShowtime(SeatType.RESERVED, 4);
    }

    private Showtime saveShowtime(SeatType seatType, int maxSeatsPerUser) {
        Performance performance = performanceRepository.save(Performance.create("자유석 선점 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, seatType,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, maxSeatsPerUser));
    }

    private Long saveGeneralSeats(Showtime showtime, int count) {
        Long first = null;
        for (int i = 1; i <= count; i++) {
            Long id = seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i))).getId();
            if (first == null) {
                first = id;
            }
        }
        return first;
    }

    private long seatsWithStatus(Showtime showtime, SeatStatus status) {
        return seatRepository.findByShowtimeIdOrderByIdAsc(showtime.getId()).stream()
                .filter(seat -> seat.getStatus() == status)
                .count();
    }
}
