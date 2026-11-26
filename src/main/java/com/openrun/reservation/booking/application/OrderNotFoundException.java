package com.openrun.reservation.booking.application;

import com.openrun.reservation.common.web.ApiProblemException;
import org.springframework.http.HttpStatus;

public class OrderNotFoundException extends ApiProblemException {

    public OrderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다.");
    }
}
