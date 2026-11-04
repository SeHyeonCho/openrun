package com.openrun.reservation.booking.application;

import jakarta.validation.constraints.NotNull;

public record ConfirmOrderRequest(@NotNull Long userId) {
}
