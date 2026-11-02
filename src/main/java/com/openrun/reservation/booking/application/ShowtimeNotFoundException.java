package com.openrun.reservation.booking.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ShowtimeNotFoundException extends ErrorResponseException {

    public ShowtimeNotFoundException(Long showtimeId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "회차를 찾을 수 없습니다. showtimeId=" + showtimeId),
                null);
    }
}
