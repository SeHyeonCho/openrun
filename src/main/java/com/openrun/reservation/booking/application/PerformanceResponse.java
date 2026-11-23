package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.Performance;
import com.openrun.reservation.booking.domain.SalesStatus;
import com.openrun.reservation.booking.domain.SeatType;
import com.openrun.reservation.booking.domain.Showtime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PerformanceResponse(
        Long id,
        String title,
        String venue,
        String posterUrl,
        List<ShowtimeSummary> showtimes,
        Instant serverTime) {

    static PerformanceResponse of(Performance performance, List<Showtime> showtimes, Map<Long, Long> remaining,
            Instant now) {
        return new PerformanceResponse(performance.getId(), performance.getTitle(), performance.getVenue(),
                performance.getPosterUrl(),
                showtimes.stream()
                        .map(showtime -> ShowtimeSummary.of(showtime, remaining.getOrDefault(showtime.getId(), 0L), now))
                        .toList(),
                now);
    }

    public record ShowtimeSummary(
            Long id,
            Instant startsAt,
            Instant endsAt,
            Instant salesOpensAt,
            Instant salesClosesAt,
            int holdMinutes,
            SeatType seatKind,
            SalesStatus salesStatus,
            long remaining,
            int maxSeatsPerBuyer) {

        static ShowtimeSummary of(Showtime showtime, long remaining, Instant now) {
            return new ShowtimeSummary(showtime.getId(), showtime.getStartsAt(), showtime.getEndsAt(),
                    showtime.getSalesOpenAt(), showtime.getSalesCloseAt(), showtime.getHoldMinutes(),
                    showtime.getSeatType(), showtime.salesStatusAt(now, remaining), remaining,
                    showtime.getMaxSeatsPerUser());
        }
    }
}
