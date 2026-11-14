package com.openrun.reservation.booking.application;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderConfirmRejection {
    ORDER_EXPIRED(HttpStatus.CONFLICT);

    private final HttpStatus status;
}
