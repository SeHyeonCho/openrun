package com.openrun.reservation.booking.application;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class PerformanceNotFoundException extends ErrorResponseException {

    public PerformanceNotFoundException(Long performanceId) {
        super(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                        "공연을 찾을 수 없습니다. performanceId=" + performanceId),
                null);
    }
}
