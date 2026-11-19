package com.openrun.reservation.booking.application;

import lombok.Getter;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

@Getter
public class OrderRejectedException extends ErrorResponseException {

    private final OrderRejection rejection;

    public OrderRejectedException(OrderRejection rejection, String detail) {
        super(rejection.getStatus(), problemDetail(rejection, detail), null);
        this.rejection = rejection;
    }

    private static ProblemDetail problemDetail(OrderRejection rejection, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(rejection.getStatus(), detail);
        problemDetail.setProperty("code", rejection.name());
        return problemDetail;
    }
}
