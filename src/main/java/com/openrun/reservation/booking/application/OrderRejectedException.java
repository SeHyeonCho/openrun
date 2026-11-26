package com.openrun.reservation.booking.application;

import com.openrun.reservation.common.web.ApiProblemException;
import lombok.Getter;

@Getter
public class OrderRejectedException extends ApiProblemException {

    private final OrderRejection rejection;

    public OrderRejectedException(OrderRejection rejection, String detail) {
        super(rejection.getStatus(), rejection.name(), detail);
        this.rejection = rejection;
    }
}
