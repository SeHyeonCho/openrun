package com.openrun.reservation.booking.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class OrderNotFoundException extends ErrorResponseException {

    public OrderNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."),
                null);
    }
}
