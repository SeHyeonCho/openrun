package com.openrun.reservation.booking.api;

import static com.openrun.reservation.TestLogin.loginAs;
import static com.openrun.reservation.TestLogin.publicIdOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.SeatType;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class HoldIdempotencyApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("선점 요청 식별자나 구매자 확인 헤더가 없거나 식별자가 UUID가 아니면 400을 반환하고 주문을 만들지 않는다")
    void rejectsMissingOrMalformedHeaders() throws Exception {
        Showtime showtime = generalShowtime(3, 4);
        String body = quantityBody(showtime, 1);

        mockMvc.perform(base(1L).header("Expected-Buyer-Id", publicIdOf(1L)).content(body))
                .andExpect(status().isBadRequest());
        mockMvc.perform(base(1L).header("Idempotency-Key", UUID.randomUUID().toString()).content(body))
                .andExpect(status().isBadRequest());
        hold(1L, "not-a-uuid", body).andExpect(status().isBadRequest());

        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
        assertThat(count("SELECT COUNT(*) FROM hold_request")).isZero();
    }

    @Test
    @DisplayName("구매자 확인 헤더가 로그인한 구매자와 다르면 409 BUYER_CHANGED로 거부하고 주문과 기록을 만들지 않는다")
    void rejectsChangedBuyer() throws Exception {
        Showtime showtime = generalShowtime(3, 4);

        mockMvc.perform(base(1L)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("Expected-Buyer-Id", publicIdOf(2L))
                        .content(quantityBody(showtime, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUYER_CHANGED"));

        assertThat(count("SELECT COUNT(*) FROM orders")).isZero();
        assertThat(count("SELECT COUNT(*) FROM hold_request")).isZero();
    }

    @Test
    @DisplayName("같은 식별자, 같은 내용으로 다시 보내면 주문을 하나 더 만들지 않고 처음 주문의 지금 상태를 201로 돌려준다")
    void replaysSuccessWithCurrentOrderState() throws Exception {
        Showtime showtime = generalShowtime(3, 4);
        String key = UUID.randomUUID().toString();

        String first = hold(1L, key, quantityBody(showtime, 2)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(first, "$.token");
        mockMvc.perform(post("/api/orders/{token}/confirm", token).with(loginAs(1L)).with(csrf()))
                .andExpect(status().isOk());

        hold(1L, key, quantityBody(showtime, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(token))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.entryNumbers", contains(1, 2)));
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
    }

    @Test
    @DisplayName("좌석 순서와 예매자 이름의 앞뒤 공백만 다르면 같은 내용으로 보고 처음 결과를 돌려준다")
    void treatsReorderedSeatsAndTrimmedNameAsSameContent() throws Exception {
        Showtime showtime = reservedShowtime();
        Long first = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        Long second = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A2")).getId();
        String key = UUID.randomUUID().toString();

        String token = JsonPath.read(hold(1L, key, seatBody(showtime, first + ", " + second, "밤산책"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.token");

        hold(1L, key, seatBody(showtime, second + ", " + first, "  밤산책 "))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value(token));
        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 식별자를 다른 내용으로 보내면 422 IDEMPOTENCY_KEY_REUSED로 거부하고 아무것도 바꾸지 않는다")
    void rejectsReusedKeyWithDifferentContent() throws Exception {
        Showtime showtime = generalShowtime(3, 4);
        String key = UUID.randomUUID().toString();
        hold(1L, key, quantityBody(showtime, 1)).andExpect(status().isCreated());

        hold(1L, key, quantityBody(showtime, 2))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM seat WHERE status = 'HELD'")).isEqualTo(1);
    }

    @Test
    @DisplayName("규칙 위반으로 끝난 요청을 같은 식별자로 다시 보내면, 그사이 좌석이 풀려도 기록한 409와 code를 그대로 돌려준다")
    void replaysRecordedRejection() throws Exception {
        Showtime showtime = reservedShowtime();
        Long seatId = seatRepository.save(Seat.forSale(showtime, SeatType.RESERVED, "A1")).getId();
        hold(2L, UUID.randomUUID().toString(), seatBody(showtime, String.valueOf(seatId), "다른 구매자"))
                .andExpect(status().isCreated());
        String key = UUID.randomUUID().toString();
        hold(1L, key, seatBody(showtime, String.valueOf(seatId), "밤산책"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"));
        entityManager.flush();
        jdbcTemplate.update("UPDATE orders SET status = 'EXPIRED' WHERE buyer_id = 2");
        jdbcTemplate.update("UPDATE seat SET status = 'AVAILABLE', current_order_id = NULL, held_until = NULL");
        entityManager.clear();

        hold(1L, key, seatBody(showtime, String.valueOf(seatId), "밤산책"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.seatIds", contains(seatId.intValue())));
        hold(1L, UUID.randomUUID().toString(), seatBody(showtime, String.valueOf(seatId), "밤산책"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("식별자는 구매자마다 따로 보고, 400과 404로 끝난 요청은 기록하지 않아 같은 식별자로 다시 처리한다")
    void keysArePerBuyerAndFailuresBeforeRulesAreNotRecorded() throws Exception {
        Showtime showtime = generalShowtime(5, 4);
        String key = UUID.randomUUID().toString();

        hold(1L, key, """
                {"showtimeId": %d, "buyerName": "밤산책"}
                """.formatted(showtime.getId())).andExpect(status().isBadRequest());
        hold(1L, key, """
                {"showtimeId": %d, "quantity": 1, "buyerName": "밤산책"}
                """.formatted(Long.MAX_VALUE)).andExpect(status().isNotFound());
        hold(1L, key, quantityBody(showtime, 1)).andExpect(status().isCreated());
        hold(2L, key, quantityBody(showtime, 2)).andExpect(status().isCreated());

        assertThat(count("SELECT COUNT(*) FROM orders")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM hold_request")).isEqualTo(2);
    }

    @Test
    @DisplayName("좌석 목록이 길어도 요청을 기록해, 1인 최대 좌석 수를 넘은 요청을 다시 보내면 기록한 409를 돌려준다")
    void recordsLongSeatListRejectedByLimit() throws Exception {
        Showtime showtime = saveShowtime(SeatType.RESERVED, 1);
        String seatIds = LongStream.rangeClosed(1, 300).mapToObj(String::valueOf).collect(Collectors.joining(","));
        String key = UUID.randomUUID().toString();

        hold(1L, key, seatBody(showtime, seatIds, "밤산책"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_EXCEEDED"));
        hold(1L, key, seatBody(showtime, seatIds, "밤산책"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_EXCEEDED"));

        assertThat(count("SELECT COUNT(*) FROM hold_request")).isEqualTo(1);
    }

    @Test
    @DisplayName("거부한 좌석 목록이 길어도 기록해, 같은 요청을 다시 보내면 기록한 409와 좌석 목록을 그대로 돌려준다")
    void recordsLongRejectedSeatList() throws Exception {
        Showtime showtime = saveShowtime(SeatType.RESERVED, 100);
        List<Long> outside = LongStream.rangeClosed(1, 60).map(i -> Long.MAX_VALUE - i).sorted().boxed().toList();
        String seatIds = outside.stream().map(String::valueOf).collect(Collectors.joining(","));
        String key = UUID.randomUUID().toString();

        hold(1L, key, seatBody(showtime, seatIds, "밤산책"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWTIME"));
        String replayed = hold(1L, key, seatBody(showtime, seatIds, "밤산책"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWTIME"))
                .andReturn().getResponse().getContentAsString();

        List<Number> replayedSeatIds = JsonPath.read(replayed, "$.seatIds");
        assertThat(replayedSeatIds).extracting(Number::longValue).containsExactlyElementsOf(outside);
        assertThat(count("SELECT COUNT(*) FROM hold_request")).isEqualTo(1);
    }

    @Test
    @DisplayName("거부한 좌석 목록이 64KB를 넘어도 기록해, 같은 요청을 다시 보내면 기록한 좌석 목록을 그대로 돌려준다")
    void recordsRejectedSeatListLongerThanText() throws Exception {
        Showtime showtime = saveShowtime(SeatType.RESERVED, 4000);
        List<Long> outside = LongStream.rangeClosed(1, 3300).map(i -> Long.MAX_VALUE - i).sorted().boxed().toList();
        String seatIds = outside.stream().map(String::valueOf).collect(Collectors.joining(","));
        String key = UUID.randomUUID().toString();

        hold(1L, key, seatBody(showtime, seatIds, "밤산책"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_NOT_IN_SHOWTIME"));
        String replayed = hold(1L, key, seatBody(showtime, seatIds, "밤산책"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        List<Number> replayedSeatIds = JsonPath.read(replayed, "$.seatIds");
        assertThat(replayedSeatIds).extracting(Number::longValue).containsExactlyElementsOf(outside);
    }

    private ResultActions hold(Long buyerId, String key, String body) throws Exception {
        return mockMvc.perform(base(buyerId)
                .header("Idempotency-Key", key)
                .header("Expected-Buyer-Id", publicIdOf(buyerId))
                .content(body));
    }

    private MockHttpServletRequestBuilder base(Long buyerId) {
        return post("/api/orders").with(loginAs(buyerId)).with(csrf()).contentType(MediaType.APPLICATION_JSON);
    }

    private String quantityBody(Showtime showtime, int quantity) {
        return """
                {"showtimeId": %d, "quantity": %d, "buyerName": "밤산책"}
                """.formatted(showtime.getId(), quantity);
    }

    private String seatBody(Showtime showtime, String seatIds, String buyerName) {
        return """
                {"showtimeId": %d, "seatIds": [%s], "buyerName": "%s"}
                """.formatted(showtime.getId(), seatIds, buyerName);
    }

    private Showtime generalShowtime(int seats, int maxSeatsPerUser) {
        Showtime showtime = saveShowtime(SeatType.GENERAL, maxSeatsPerUser);
        for (int i = 1; i <= seats; i++) {
            seatRepository.save(Seat.forSale(showtime, SeatType.GENERAL, String.valueOf(i)));
        }
        return showtime;
    }

    private Showtime reservedShowtime() {
        return saveShowtime(SeatType.RESERVED, 4);
    }

    private Showtime saveShowtime(SeatType seatType, int maxSeatsPerUser) {
        Performance performance = performanceRepository.save(Performance.create("요청 식별자 테스트 공연", "테스트 공연장"));
        Instant now = Instant.now();
        Instant startsAt = now.plus(Duration.ofDays(7));
        return showtimeRepository.save(Showtime.create(performance, seatType,
                startsAt, startsAt.plus(Duration.ofHours(2)), now.minus(Duration.ofHours(1)), 10, maxSeatsPerUser));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
