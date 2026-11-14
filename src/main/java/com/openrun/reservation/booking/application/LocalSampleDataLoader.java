package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.SeatType;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalSampleDataLoader implements ApplicationRunner {

    private static final int GENERAL_ADMISSION_COUNT = 50;
    private static final int GENERAL_ONSITE_COUNT = 5;
    private static final Duration RUNNING_TIME = Duration.ofHours(2);

    private final PerformanceRepository performanceRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (performanceRepository.count() > 0) {
            return;
        }
        Performance performance = performanceRepository.save(Performance.create("샘플 밴드 단독 공연", "샘플 라이브홀"));
        Instant salesOpenAt = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant firstDay = salesOpenAt.plus(Duration.ofDays(14));
        Instant secondDay = firstDay.plus(Duration.ofDays(1));

        Showtime reservedShow = showtimeRepository.save(
                Showtime.create(performance, firstDay, firstDay.plus(RUNNING_TIME), salesOpenAt, 10, 4));
        seatRepository.saveAll(reservedSeats(reservedShow));

        Showtime generalShow = showtimeRepository.save(
                Showtime.create(performance, secondDay, secondDay.plus(RUNNING_TIME), salesOpenAt, 10, 4));
        seatRepository.saveAll(generalSeats(generalShow));
    }

    private List<Seat> reservedSeats(Showtime showtime) {
        List<Seat> seats = new ArrayList<>();
        for (String row : List.of("A", "B")) {
            for (int number = 1; number <= 10; number++) {
                String label = row + number;
                seats.add(number == 10
                        ? Seat.notForSale(showtime, SeatType.RESERVED, label)
                        : Seat.forSale(showtime, SeatType.RESERVED, label));
            }
        }
        return seats;
    }

    private List<Seat> generalSeats(Showtime showtime) {
        List<Seat> seats = new ArrayList<>();
        for (int number = 1; number <= GENERAL_ADMISSION_COUNT; number++) {
            String label = String.valueOf(number);
            seats.add(number > GENERAL_ADMISSION_COUNT - GENERAL_ONSITE_COUNT
                    ? Seat.notForSale(showtime, SeatType.GENERAL, label)
                    : Seat.forSale(showtime, SeatType.GENERAL, label));
        }
        return seats;
    }
}
