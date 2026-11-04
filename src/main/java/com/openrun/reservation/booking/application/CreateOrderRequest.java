package com.openrun.reservation.booking.application;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Long showtimeId,
        @NotNull Long userId,
        @NotEmpty List<@NotNull Long> seatIds) {
}
