package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatQueryService {

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ShowtimeSeatsResponse getSeats(Long showtimeId) {
        if (!showtimeRepository.existsById(showtimeId)) {
            throw new ShowtimeNotFoundException(showtimeId);
        }
        Instant now = clock.instant();
        List<SeatResponse> seats = seatRepository.findByShowtimeIdOrderByIdAsc(showtimeId).stream()
                .map(seat -> SeatResponse.of(seat, now))
                .toList();
        return new ShowtimeSeatsResponse(showtimeId, seats);
    }
}
