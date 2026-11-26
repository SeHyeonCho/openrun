package com.openrun.reservation.booking.application;

import com.openrun.reservation.common.web.ApiProblemException;
import org.springframework.http.HttpStatus;

public class PerformanceNotFoundException extends ApiProblemException {

    public PerformanceNotFoundException(Long performanceId) {
        super(HttpStatus.NOT_FOUND, "공연을 찾을 수 없습니다. performanceId=" + performanceId);
    }
}
