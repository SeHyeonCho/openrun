package com.openrun.reservation.booking.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openrun.reservation.DatabaseCleaner;
import com.openrun.reservation.MutableClock;
import com.openrun.reservation.MutableClockConfiguration;
import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.SalesStatus;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class, MutableClockConfiguration.class})
class LocalSampleDataLoaderTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:30:00Z");

    @Autowired
    private PerformanceRepository performanceRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private PerformanceQueryService performanceQueryService;

    @Autowired
    private MutableClock clock;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private LocalSampleDataLoader loader;

    @BeforeEach
    void setUp() {
        loader = new LocalSampleDataLoader(performanceRepository, showtimeRepository, seatRepository, clock);
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("빈 DB에서는 포스터가 있는 샘플 공연과 판매 중인 회차 두 개를 만든다")
    void createsSampleWhenEmpty() {
        clock.setInstant(NOW);

        load();

        assertThat(performanceRepository.findAll()).singleElement()
                .satisfies(performance -> assertThat(performance.getPosterUrl()).isNotNull());
        Long performanceId = performanceRepository.findAll().getFirst().getId();
        assertThat(performanceQueryService.find(performanceId).showtimes())
                .hasSize(2)
                .allSatisfy(showtime -> assertThat(showtime.salesStatus()).isEqualTo(SalesStatus.ON_SALE));
    }

    @Test
    @DisplayName("판매가 끝나지 않은 회차가 있으면 새로 만들지 않는다")
    void skipsWhileShowtimeIsOnSale() {
        clock.setInstant(NOW);
        load();

        clock.setInstant(NOW.plus(Duration.ofDays(10)));
        load();

        assertThat(performanceRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("모든 회차의 판매가 끝났으면 기존 데이터는 두고 판매 중인 샘플 공연을 새로 만든다")
    void createsNewSampleWhenAllShowtimesClosed() {
        clock.setInstant(NOW);
        load();
        long seats = seatRepository.count();

        clock.setInstant(NOW.plus(Duration.ofDays(30)));
        load();

        assertThat(performanceRepository.count()).isEqualTo(2);
        assertThat(seatRepository.count()).isEqualTo(seats * 2);
        assertThat(showtimeRepository.existsBySalesCloseAtAfter(clock.instant())).isTrue();
    }

    private void load() {
        transactionTemplate.executeWithoutResult(status -> loader.run(null));
    }
}
