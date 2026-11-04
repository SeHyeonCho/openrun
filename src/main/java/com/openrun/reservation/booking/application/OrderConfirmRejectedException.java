package com.openrun.reservation.booking.application;

import lombok.Getter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

@Getter
public class OrderConfirmRejectedException extends ErrorResponseException {

    private final OrderConfirmRejection rejection;

    public OrderConfirmRejectedException(OrderConfirmRejection rejection, String detail) {
        super(rejection.getStatus(), problemDetail(rejection, detail), null);
        this.rejection = rejection;
    }

    private static ProblemDetail problemDetail(OrderConfirmRejection rejection, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(rejection.getStatus(), detail);
        problemDetail.setProperty("code", rejection.name());
        return problemDetail;
    }
}
