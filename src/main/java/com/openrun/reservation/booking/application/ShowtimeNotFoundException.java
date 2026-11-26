package com.openrun.reservation.booking.application;

import com.openrun.reservation.common.web.ApiProblemException;
import org.springframework.http.HttpStatus;

public class ShowtimeNotFoundException extends ApiProblemException {

    public ShowtimeNotFoundException(Long showtimeId) {
        super(HttpStatus.NOT_FOUND, "회차를 찾을 수 없습니다. showtimeId=" + showtimeId);
    }
}
