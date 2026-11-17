package com.openrun.reservation.booking.application;

public record HoldOutcome(OrderResponse response, SeatHoldRejectedException rejection) {

    static HoldOutcome accepted(OrderResponse response) {
        return new HoldOutcome(response, null);
    }

    static HoldOutcome rejected(SeatHoldRejectedException rejection) {
        return new HoldOutcome(null, rejection);
    }

    public OrderResponse orThrow() {
        if (rejection != null) {
            throw rejection;
        }
        return response;
    }
}
