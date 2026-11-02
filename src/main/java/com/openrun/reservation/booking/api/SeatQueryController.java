package com.openrun.reservation.booking.api;

import com.openrun.reservation.booking.application.SeatQueryService;
import com.openrun.reservation.booking.application.ShowtimeSeatsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/showtimes")
public class SeatQueryController {

    private final SeatQueryService seatQueryService;

    @GetMapping("/{showtimeId}/seats")
    public ShowtimeSeatsResponse getSeats(@PathVariable Long showtimeId) {
        return seatQueryService.getSeats(showtimeId);
    }
}
