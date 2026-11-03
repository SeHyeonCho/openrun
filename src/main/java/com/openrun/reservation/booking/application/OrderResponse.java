package com.openrun.reservation.booking.application;

import com.openrun.reservation.booking.domain.Order;
import com.openrun.reservation.booking.domain.OrderStatus;
import com.openrun.reservation.booking.domain.Seat;
import java.time.Instant;
import java.util.List;

public record OrderResponse(Long orderId, Long showtimeId, OrderStatus status, Instant heldUntil, List<Long> seatIds) {

    public static OrderResponse of(Order order, Long showtimeId, List<Seat> seats) {
        return new OrderResponse(order.getId(), showtimeId, order.getStatus(), order.getHeldUntil(),
                seats.stream().map(Seat::getId).toList());
    }
}
