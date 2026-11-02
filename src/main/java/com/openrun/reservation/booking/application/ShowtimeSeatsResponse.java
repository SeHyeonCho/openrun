package com.openrun.reservation.booking.application;

import java.util.List;

public record ShowtimeSeatsResponse(Long showtimeId, List<SeatResponse> seats) {
}
