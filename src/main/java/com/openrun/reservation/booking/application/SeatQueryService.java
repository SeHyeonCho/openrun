package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.SeatRepository;
import com.openrun.reservation.booking.domain.ShowtimeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatQueryService {

    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;

    @Transactional(readOnly = true)
    public ShowtimeSeatsResponse getSeats(Long showtimeId) {
        if (!showtimeRepository.existsById(showtimeId)) {
            throw new ShowtimeNotFoundException(showtimeId);
        }
        List<SeatResponse> seats = seatRepository.findByShowtimeIdOrderByIdAsc(showtimeId).stream()
                .map(SeatResponse::from)
                .toList();
        return new ShowtimeSeatsResponse(showtimeId, seats);
    }
}
