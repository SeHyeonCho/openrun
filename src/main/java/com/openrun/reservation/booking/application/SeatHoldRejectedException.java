package com.openrun.reservation.booking.application;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

@Getter
public class SeatHoldRejectedException extends ErrorResponseException {

    private final SeatHoldRejection rejection;
    private final List<Long> seatIds;

    public SeatHoldRejectedException(SeatHoldRejection rejection, String detail) {
        this(rejection, detail, List.of());
    }

    public SeatHoldRejectedException(SeatHoldRejection rejection, String detail, List<Long> seatIds) {
        super(HttpStatus.CONFLICT, problemDetail(rejection, detail, seatIds), null);
        this.rejection = rejection;
        this.seatIds = List.copyOf(seatIds);
    }

    private static ProblemDetail problemDetail(SeatHoldRejection rejection, String detail, List<Long> seatIds) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problemDetail.setProperty("code", rejection.name());
        if (!seatIds.isEmpty()) {
            problemDetail.setProperty("seatIds", seatIds);
        }
        return problemDetail;
    }
}
