package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatStatus;
import com.openrun.reservation.booking.domain.SeatType;
import java.time.Instant;

public record SeatResponse(Long seatId, SeatType type, String label, SeatStatus status) {

    public static SeatResponse of(Seat seat, Instant now) {
        return new SeatResponse(seat.getId(), seat.getType(), seat.getLabel(), seat.statusAt(now));
    }
}
