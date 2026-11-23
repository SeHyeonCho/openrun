package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.OrderSeatRepository;
import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.PerformanceRepository;
import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.Showtime;
import com.openrun.reservation.booking.domain.ShowtimeRemaining;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PerformanceQueryService {

    private final PerformanceRepository performanceRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final OrderSeatRepository orderSeatRepository;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PerformanceResponse find(Long performanceId) {
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new PerformanceNotFoundException(performanceId));
        Instant now = clock.instant();
        List<Showtime> showtimes = showtimeRepository.findAllByPerformanceIdOrderByStartsAtAscIdAsc(performanceId);
        Map<Long, Long> remaining = showtimes.isEmpty()
                ? Map.of()
                : seatRepository.countRemainingByShowtimeIds(showtimes.stream().map(Showtime::getId).toList(), now)
                        .stream()
                        .collect(Collectors.toMap(ShowtimeRemaining::showtimeId, ShowtimeRemaining::remaining));
        return PerformanceResponse.of(performance, showtimes, remaining, now);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AvailabilityResponse availability(Long showtimeId, Long buyerId) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ShowtimeNotFoundException(showtimeId));
        Instant now = clock.instant();
        long remaining = seatRepository.countRemainingByShowtimeIds(List.of(showtimeId), now).stream()
                .mapToLong(ShowtimeRemaining::remaining)
                .sum();
        long ownedCount = orderSeatRepository.countActiveSeats(showtimeId, buyerId, now);
        return new AvailabilityResponse(showtimeId, showtime.salesStatusAt(now, remaining), remaining,
                showtime.getMaxSeatsPerUser(), ownedCount, now);
    }
}
