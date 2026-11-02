package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.Seat;
import com.openrun.reservation.booking.domain.SeatStatus;
import com.openrun.reservation.booking.domain.SeatType;

public record SeatResponse(Long seatId, SeatType type, String label, SeatStatus status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(seat.getId(), seat.getType(), seat.getLabel(), seat.getStatus());
    }
}
