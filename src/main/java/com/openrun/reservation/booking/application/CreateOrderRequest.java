package com.openrun.reservation.booking.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotNull Long showtimeId,
        List<@NotNull Long> seatIds,
        @Positive Integer quantity,
        @NotBlank @Size(max = 20) String buyerName) {

    public CreateOrderRequest {
        if (buyerName != null) {
            buyerName = buyerName.strip();
        }
    }

    boolean choosesQuantity() {
        return quantity != null;
    }

    int requestedSeatCount() {
        return choosesQuantity() ? quantity : seatIds.size();
    }
}
