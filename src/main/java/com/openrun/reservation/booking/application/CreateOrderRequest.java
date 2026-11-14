package com.openrun.reservation.booking.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Long showtimeId,
        @NotEmpty List<@NotNull Long> seatIds,
        @NotBlank @Size(max = 20) String buyerName) {

    public CreateOrderRequest {
        if (buyerName != null) {
            buyerName = buyerName.strip();
        }
    }
}
